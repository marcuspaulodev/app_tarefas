<?php

namespace Tests\Feature\Api;

use App\Actions\Auth\ResetAccountPassword;
use App\Models\Tag;
use App\Models\Task;
use App\Models\User;
use Illuminate\Auth\Notifications\ResetPassword;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Support\Facades\Notification;
use Illuminate\Support\Facades\Password;
use PHPUnit\Framework\Attributes\DataProvider;
use Tests\TestCase;

class PasswordRecoveryTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        Notification::fake();
    }

    /** @return list<array{string, string}> */
    public static function routes(): array
    {
        return [['password.email', 'password.update'], ['api.v1.password.email', 'api.v1.password.update']];
    }

    /** @return array{email: string, token: string, password: string, password_confirmation: string} */
    private function credentials(User $user, string $token, string $password = 'nova-senha-segura'): array
    {
        return ['email' => $user->email, 'token' => $token, 'password' => $password, 'password_confirmation' => $password];
    }

    #[DataProvider('routes')]
    public function test_link_requests_do_not_reveal_accounts_or_send_duplicate_notifications(string $sendRoute, string $resetRoute): void
    {
        $user = User::factory()->create();
        $originalPassword = $user->password;
        $response = $this->postJson(route($sendRoute), ['email' => $user->email])->assertOk()->json();
        $this->postJson(route($sendRoute), ['email' => $user->email])->assertOk()->assertExactJson($response);
        $this->postJson(route($sendRoute), ['email' => 'missing@example.test'])->assertOk()->assertExactJson($response);
        Notification::assertSentToTimes($user, ResetPassword::class, 1);
        Notification::assertCount(1);
        $notification = Notification::sent($user, ResetPassword::class)->sole();
        $saved = DB::table('password_reset_tokens')->where('email', $user->email)->sole();
        $this->assertNotSame($notification->token, $saved->token);
        $this->assertTrue(Hash::check($notification->token, $saved->token));
        $this->assertSame($originalPassword, $user->fresh()->password);
        $this->assertGuest();
    }

    public function test_email_uses_portuguese_and_the_configured_url_instead_of_the_request_host(): void
    {
        config(['app.url' => 'https://tarefas.example.test']);
        $user = User::factory()->create(['email' => 'teste+android@example.test']);
        $this->postJson('https://untrusted.example.test/api/v1/forgot-password', ['email' => $user->email])->assertOk();
        $notification = Notification::sent($user, ResetPassword::class)->sole();
        $mail = $notification->toMail($user);
        $this->assertSame('Redefinir sua senha — Tarefas', $mail->subject);
        $this->assertSame('Criar nova senha', $mail->actionText);
        $this->assertStringStartsWith('https://tarefas.example.test/reset-password/'.$notification->token, $mail->actionUrl);
        parse_str(parse_url($mail->actionUrl, PHP_URL_QUERY), $query);
        $this->assertSame($user->email, $query['email']);
        $this->assertStringContainsString('60 minutos', implode(' ', $mail->outroLines));
    }

    #[DataProvider('routes')]
    public function test_reset_revokes_device_tokens_and_preserves_tasks_tags_and_other_accounts(string $sendRoute, string $resetRoute): void
    {
        $user = User::factory()->create(['password' => 'senha-anterior'])->refresh();
        $other = User::factory()->create();
        $task = Task::factory()->for($user)->create()->refresh();
        $tag = Tag::factory()->for($user)->create()->refresh();
        $task->tags()->attach($tag);
        $oldToken = $user->createToken('Android')->plainTextToken;
        $user->createToken('Outro aparelho');
        $otherToken = $other->createToken('Outra conta')->plainTextToken;
        $this->postJson(route($sendRoute), ['email' => $user->email])->assertOk();
        $token = Notification::sent($user, ResetPassword::class)->sole()->token;

        $this->postJson(route($resetRoute), $this->credentials($user, $token))->assertOk()
            ->assertJsonPath('message', 'Senha alterada. Entre novamente no site e no app com a nova senha.');
        $this->assertTrue(Hash::check('nova-senha-segura', $user->fresh()->password));
        $this->assertNotSame($user->remember_token, $user->fresh()->remember_token);
        $this->assertDatabaseMissing('password_reset_tokens', ['email' => $user->email]);
        $this->assertSame(0, $user->tokens()->count());
        $this->assertSame(1, $other->tokens()->count());
        $this->assertSame($task->getAttributes(), $task->fresh()->getAttributes());
        $this->assertSame($tag->getAttributes(), $tag->fresh()->getAttributes());
        $this->assertDatabaseHas('task_tag', ['task_id' => $task->id, 'tag_id' => $tag->id]);
        $this->assertSame($user->sync_version, $user->fresh()->sync_version);
        $this->assertGuest();

        $this->app['auth']->forgetGuards();
        $this->withToken($oldToken)->getJson(route('api.v1.user'))->assertUnauthorized();
        $this->app['auth']->forgetGuards();
        $this->withToken($otherToken)->getJson(route('api.v1.user'))->assertOk()->assertJsonPath('data.id', $other->id);
        $this->app['auth']->forgetGuards();
        $this->postJson(route('api.v1.login'), ['email' => $user->email, 'password' => 'senha-anterior', 'device_name' => 'Teste'])
            ->assertUnprocessable();
        $this->postJson(route('api.v1.login'), ['email' => $user->email, 'password' => 'nova-senha-segura', 'device_name' => 'Teste'])
            ->assertOk()->assertJsonPath('user.id', $user->id);
    }

    public function test_invalid_expired_and_foreign_tokens_cannot_change_the_password(): void
    {
        $user = User::factory()->create();
        $other = User::factory()->create();
        $token = Password::broker('users')->createToken($user);
        $user->createToken('Android');
        $bad = $this->postJson(route('api.v1.password.update'), $this->credentials($user, 'invalid-token'))
            ->assertUnprocessable()->assertJsonValidationErrors('token')->json('errors');
        $this->postJson(route('api.v1.password.update'), $this->credentials($other, $token))
            ->assertUnprocessable()->assertJsonPath('errors', $bad);
        $this->postJson(route('api.v1.password.update'), array_replace($this->credentials($user, $token), ['email' => 'missing@example.test']))
            ->assertUnprocessable()->assertJsonPath('errors', $bad);
        $this->travel(61)->minutes();
        $this->postJson(route('api.v1.password.update'), $this->credentials($user, $token))
            ->assertUnprocessable()->assertJsonPath('errors', $bad);
        $this->assertSame($user->password, $user->fresh()->password);
        $this->assertSame($other->password, $other->fresh()->password);
        $this->assertSame(1, $user->tokens()->count());
    }

    public function test_a_successful_link_cannot_be_reused(): void
    {
        $user = User::factory()->create();
        $token = Password::broker('users')->createToken($user);
        $this->postJson(route('password.update'), $this->credentials($user, $token))->assertOk();
        $this->postJson(route('password.update'), $this->credentials($user, $token, 'outra-senha-segura'))
            ->assertUnprocessable()->assertJsonValidationErrors('token');
        $this->assertTrue(Hash::check('nova-senha-segura', $user->fresh()->password));
    }

    public function test_requesting_a_new_link_after_the_cooldown_invalidates_the_old_one(): void
    {
        $user = User::factory()->create();
        $this->postJson(route('password.email'), ['email' => $user->email])->assertOk();
        $first = Notification::sent($user, ResetPassword::class)->sole()->token;
        $this->travel(61)->seconds();
        $this->postJson(route('password.email'), ['email' => $user->email])->assertOk();
        $second = Notification::sent($user, ResetPassword::class)->last()->token;
        $this->assertNotSame($first, $second);
        $this->postJson(route('password.update'), $this->credentials($user, $first))->assertUnprocessable();
        $this->postJson(route('password.update'), $this->credentials($user, $second))->assertOk();
    }

    public function test_validation_preserves_the_token_for_correction(): void
    {
        $user = User::factory()->create();
        $token = Password::broker('users')->createToken($user);
        $this->postJson(route('password.email'), ['email' => 'not-an-email'])->assertUnprocessable()->assertJsonValidationErrors('email');
        $this->postJson(route('password.update'), $this->credentials($user, $token, 'short'))->assertUnprocessable()->assertJsonValidationErrors('password');
        $this->postJson(route('password.update'), array_replace($this->credentials($user, $token), ['password_confirmation' => 'different']))
            ->assertUnprocessable()->assertJsonValidationErrors('password');
        $this->assertTrue(Password::broker('users')->tokenExists($user, $token));
        $this->assertSame($user->password, $user->fresh()->password);
        Notification::assertNothingSent();
    }

    public function test_reset_invalidates_an_existing_web_session_even_before_its_first_api_call(): void
    {
        $user = User::factory()->create(['password' => 'senha-anterior']);
        $this->postJson(route('login'), ['email' => $user->email, 'password' => 'senha-anterior'])
            ->assertOk()->assertSessionHas('password_hash_web');
        $token = Password::broker('users')->createToken($user);
        app(ResetAccountPassword::class)->handle($this->credentials($user, $token));
        $this->app['auth']->forgetGuards();
        $this->withHeader('Origin', 'http://localhost')->getJson(route('api.v1.user'))->assertUnauthorized();
    }

    public function test_reset_deletes_only_the_accounts_database_sessions(): void
    {
        config(['session.driver' => 'database']);
        $user = User::factory()->create();
        $other = User::factory()->create();
        foreach ([$user, $other] as $account) {
            DB::table('sessions')->insert(['id' => 'session-'.$account->id, 'user_id' => $account->id,
                'payload' => base64_encode(serialize([])), 'last_activity' => now()->timestamp]);
        }
        $token = Password::broker('users')->createToken($user);
        $this->postJson(route('api.v1.password.update'), $this->credentials($user, $token))->assertOk();
        $this->assertDatabaseMissing('sessions', ['id' => 'session-'.$user->id]);
        $this->assertDatabaseHas('sessions', ['id' => 'session-'.$other->id]);
    }

    public function test_a_reset_in_the_current_browser_logs_it_out(): void
    {
        $user = User::factory()->create();
        $token = Password::broker('users')->createToken($user);
        $this->actingAs($user, 'web')->postJson(route('password.update'), $this->credentials($user, $token))->assertOk();
        $this->assertGuest('web');
    }

    public function test_recovery_pages_open_without_login_and_do_not_cache_or_forward_the_link(): void
    {
        $this->withoutVite();
        $this->get(route('password.request'))->assertOk()->assertViewIs('app')
            ->assertViewHas('passwordRecovery', ['mode' => 'forgot', 'token' => '', 'email' => '']);
        $response = $this->get(route('password.reset', ['token' => 'example-token', 'email' => 'test@example.test']))
            ->assertOk()->assertViewIs('app')->assertHeader('Referrer-Policy', 'no-referrer')
            ->assertViewHas('passwordRecovery', ['mode' => 'reset', 'token' => 'example-token', 'email' => 'test@example.test']);
        $this->assertStringContainsString('no-store', $response->headers->get('Cache-Control'));
        $this->get(route('password.reset', ['token' => 'example-token', 'email' => ['invalid']]))
            ->assertOk()->assertViewHas('passwordRecovery', ['mode' => 'reset', 'token' => 'example-token', 'email' => '']);
    }

    public function test_web_requests_require_csrf_but_android_can_request_a_link_without_a_session(): void
    {
        $this->app->detectEnvironment(fn (): string => 'local');
        $this->postJson(route('password.email'), ['email' => 'test@example.test'])->assertStatus(419);
        $this->postJson(route('password.update'), [])->assertStatus(419);
        $this->postJson(route('api.v1.password.email'), ['email' => 'test@example.test'])->assertOk();
    }

    /** @return list<array{string, int}> */
    public static function throttledRoutes(): array
    {
        return [['password.email', 200], ['api.v1.password.email', 200], ['password.update', 422], ['api.v1.password.update', 422]];
    }

    #[DataProvider('throttledRoutes')]
    public function test_recovery_requests_are_rate_limited(string $route, int $status): void
    {
        $body = ['email' => 'missing@example.test', 'token' => 'invalid', 'password' => 'nova-senha-segura', 'password_confirmation' => 'nova-senha-segura'];
        for ($attempt = 0; $attempt < 6; $attempt++) {
            $this->postJson(route($route), $body)->assertStatus($status);
        }
        $this->postJson(route($route), $body)->assertTooManyRequests();
    }
}
