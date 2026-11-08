<?php

namespace Tests\Feature\Api;

use App\Models\User;
use Illuminate\Http\Client\ConnectionException;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Http;
use Symfony\Component\Process\Process;
use Tests\TestCase;

class AndroidSyncIntegrationTest extends TestCase
{
    public function test_android_room_and_http_client_synchronize_with_laravel_and_mysql(): void
    {
        if (getenv('RUN_ANDROID_SYNC_TESTS') !== '1') {
            $this->markTestSkipped('Integração Android opt-in: RUN_ANDROID_SYNC_TESTS=1.');
        }

        $this->assertTrue(app()->environment('testing'));
        $connection = DB::connection();
        $this->assertSame('mysql', $connection->getDriverName());
        $this->assertSame('tarefas_test', $connection->getDatabaseName(), 'Use somente o banco isolado de testes.');
        $this->assertSame(0, $connection->transactionLevel(), 'O servidor HTTP precisa enxergar dados confirmados.');
        $this->artisan('migrate', ['--force' => true])->assertSuccessful();

        $runId = bin2hex(random_bytes(12));
        $prefix = 'sync-'.$runId.'-';
        $listener = stream_socket_server('tcp://127.0.0.1:0');
        $this->assertIsResource($listener);
        $address = stream_socket_get_name($listener, false);
        fclose($listener);
        $url = 'http://'.$address;
        $server = new Process([
            getenv('SYNC_TEST_PHP') ?: PHP_BINARY, '-S', $address,
            base_path('vendor/laravel/framework/src/Illuminate/Foundation/resources/server.php'),
        ], public_path(), [
            'APP_ENV' => 'testing', 'APP_DEBUG' => 'false', 'APP_URL' => $url,
            'APP_KEY' => config('app.key'),
            'APP_CONFIG_CACHE' => sys_get_temp_dir().'/tarefas-sync-'.$runId.'.php',
            'DB_CONNECTION' => 'mysql', 'DB_DATABASE' => 'tarefas_test', 'DB_URL' => '',
            'DB_HOST' => (string) $connection->getConfig('host'),
            'DB_PORT' => (string) $connection->getConfig('port'),
            'DB_USERNAME' => (string) $connection->getConfig('username'),
            'DB_PASSWORD' => (string) $connection->getConfig('password'),
            'DB_SOCKET' => (string) $connection->getConfig('unix_socket'),
            'CACHE_STORE' => 'array', 'SESSION_DRIVER' => 'array', 'QUEUE_CONNECTION' => 'sync',
            'MAIL_MAILER' => 'array', 'BCRYPT_ROUNDS' => '4', 'LOG_CHANNEL' => 'stderr',
            'PHP_CLI_SERVER_WORKERS' => false,
        ]);

        $android = dirname(base_path()).'/android';
        $report = $android.'/app/build/test-results/testDebugUnitTest/TEST-com.marcuspaulo.tarefas.sync.SyncIntegrationTest.xml';
        $gradle = null;
        try {
            $server->start();
            $this->waitForServer($server, $url);
            if (is_file($report)) {
                unlink($report);
            }
            $gradle = new Process([
                getenv('SYNC_TEST_GRADLE') ?: 'gradle', '--no-daemon', '--console=plain',
                ':app:testDebugUnitTest', '--tests', 'com.marcuspaulo.tarefas.sync.SyncIntegrationTest', '--rerun-tasks',
            ], $android, ['SYNC_TEST_URL' => $url, 'SYNC_TEST_PREFIX' => $prefix]);
            $gradle->setTimeout(600);
            $gradle->run();
            $this->assertTrue($gradle->isSuccessful(), substr($gradle->getOutput().$gradle->getErrorOutput(), -16000));
            $this->assertFileExists($report);
            $results = simplexml_load_file($report);
            $this->assertNotFalse($results);
            $this->assertGreaterThan(0, (int) $results['tests']);
            $this->assertSame(0, (int) $results['skipped'], 'A integração precisa executar, sem pular cenários.');
            $this->assertSame(0, (int) $results['failures']);
            $this->assertSame(0, (int) $results['errors']);
        } finally {
            $gradle?->stop(1);
            $server->stop(1);
            // As contas são criadas pela API real. Só os dados desta execução são removidos.
            DB::transaction(function () use ($prefix): void {
                foreach (User::where('email', 'like', $prefix.'%@example.test')->get() as $user) {
                    $user->tokens()->delete();
                    $user->delete();
                }
            });
        }
    }

    private function waitForServer(Process $server, string $url): void
    {
        $deadline = microtime(true) + 10;
        do {
            $this->assertTrue($server->isRunning(), $server->getErrorOutput());
            try {
                if (Http::connectTimeout(1)->timeout(1)->get($url.'/up')->ok()) {
                    return;
                }
            } catch (ConnectionException) {
                // O processo ainda está iniciando.
            }
            usleep(100000);
        } while (microtime(true) < $deadline);

        $this->fail('O servidor de teste não iniciou. '.$server->getErrorOutput());
    }
}
