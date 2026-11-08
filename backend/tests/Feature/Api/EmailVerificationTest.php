<?php

namespace Tests\Feature\Api;

use App\Models\Tag;
use App\Models\Task;
use App\Models\User;
use Illuminate\Auth\Events\Verified;
use Illuminate\Auth\Notifications\VerifyEmail;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Illuminate\Support\Facades\Event;
use Illuminate\Support\Facades\Notification;
use PHPUnit\Framework\Attributes\DataProvider;
use RuntimeException;
use Tests\TestCase;

class EmailVerificationTest extends TestCase
{
    use RefreshDatabase;

    protected function setUp(): void
    {
        parent::setUp();
        Notification::fake();
        $this->withoutVite();
    }

    /** @return list<array{string, string}> */
    public static function registrationRoutes(): array
    {
        return [['register', 'data'], ['api.v1.register', 'user']];
    }

    /** @return array<string, string> */
    private function registration(): array
    {
        return ['name' => 'Teste', 'email' => 'verification@example.test', 'password' => 'senha-segura-123',
            'password_confirmation' => 'senha-segura-123', 'device_name' => 'Android'];
    }

    private function link(User $user): string
    {
        return (new VerifyEmail)->toMail($user)->actionUrl;
    }

    #[DataProvider('registrationRoutes')]
    public function test_registration_sends_confirmation_and_keeps_the_account_usable(string $route, string $key): void
    {
        $response = $this->postJson(route($route), $this->registration())->assertCreated()
            ->assertJsonPath($key.'.email_verified', false);
        $user = User::where('email', 'verification@example.test')->sole();
        Notification::assertSentToTimes($user, VerifyEmail::class, 1);
        $this->assertCount(3, $user->tags);
        if ($route === 'api.v1.register') {
            $this->withToken($response->json('token'));
        } else {
            $this->actingAs($user, 'web');
        }
        $this->postJson(route('api.v1.verification.send'))->assertTooManyRequests();
        $this->getJson(route('api.v1.tags.index'))->assertOk();
        Notification::assertCount(1);
    }

    #[DataProvider('registrationRoutes')]
    public function test_mail_failure_preserves_registration_and_allows_resending(string $route, string $key): void
    {
        Notification::shouldReceive('send')->once()->andThrow(new RuntimeException('SMTP indisponível no teste'));
        $this->postJson(route($route), $this->registration())->assertCreated()->assertJsonPath($key.'.email_verified', false);
        $user = User::where('email', 'verification@example.test')->sole();
        $this->assertFalse($user->hasVerifiedEmail());
        Notification::fake();
        $this->actingAs($user, 'web')->postJson(route('api.v1.verification.send'))->assertOk();
        Notification::assertSentToTimes($user, VerifyEmail::class, 1);
    }

    public function test_resending_requires_authentication_and_uses_only_the_current_account(): void
    {
        $this->postJson(route('api.v1.verification.send'))->assertUnauthorized();
        $user = User::factory()->unverified()->create();
        $other = User::factory()->unverified()->create();
        $this->withToken($user->createToken('Android')->plainTextToken)
            ->postJson(route('api.v1.verification.send'), ['email' => $other->email, 'id' => $other->id])
            ->assertOk()->assertJsonPath('email_verified', false);
        Notification::assertSentToTimes($user, VerifyEmail::class, 1);
        Notification::assertNotSentTo($other, VerifyEmail::class);
    }

    public function test_resend_cooldown_is_shared_by_devices_but_not_by_accounts(): void
    {
        $user = User::factory()->unverified()->create();
        $other = User::factory()->unverified()->create();
        $firstToken = $user->createToken('Primeiro aparelho')->plainTextToken;
        $secondToken = $user->createToken('Segundo aparelho')->plainTextToken;
        $this->withToken($firstToken)->postJson(route('api.v1.verification.send'))->assertOk();
        $this->app['auth']->forgetGuards();
        $this->withToken($secondToken)->postJson(route('api.v1.verification.send'))->assertTooManyRequests()->assertHeader('Retry-After');
        $this->app['auth']->forgetGuards();
        $this->withToken($other->createToken('Outra conta')->plainTextToken)->postJson(route('api.v1.verification.send'))->assertOk();
        $this->travel(61)->seconds();
        $this->app['auth']->forgetGuards();
        $this->withToken($firstToken)->postJson(route('api.v1.verification.send'))->assertOk();
        Notification::assertSentToTimes($user, VerifyEmail::class, 2);
        Notification::assertSentToTimes($other, VerifyEmail::class, 1);
    }

    public function test_confirmed_accounts_do_not_receive_another_message(): void
    {
        $user = User::factory()->create();
        $this->actingAs($user, 'web')->postJson(route('api.v1.verification.send'))->assertOk()
            ->assertJsonPath('email_verified', true);
        Notification::assertNothingSent();
    }

    public function test_public_confirmation_is_idempotent_and_preserves_account_data(): void
    {
        Event::fake([Verified::class]);
        $user = User::factory()->unverified()->create()->refresh();
        $task = Task::factory()->for($user)->create()->refresh();
        $tag = Tag::factory()->for($user)->create()->refresh();
        $task->tags()->attach($tag);
        $token = $user->createToken('Android')->plainTextToken;
        $link = $this->link($user);
        $response = $this->get($link)->assertOk()->assertViewIs('app')
            ->assertViewHas('emailVerification', ['verified' => true])->assertHeader('Referrer-Policy', 'no-referrer');
        $this->assertStringContainsString('no-store', $response->headers->get('Cache-Control'));
        $verifiedAt = $user->fresh()->email_verified_at;
        $this->assertNotNull($verifiedAt);
        $this->travel(1)->minutes();
        $this->get($link)->assertOk();
        $this->assertEquals($verifiedAt, $user->fresh()->email_verified_at);
        Event::assertDispatchedTimes(Verified::class, 1);
        $this->assertGuest();
        $this->assertSame($task->getAttributes(), $task->fresh()->getAttributes());
        $this->assertSame($tag->getAttributes(), $tag->fresh()->getAttributes());
        $this->assertSame($user->sync_version, $user->fresh()->sync_version);
        $this->assertDatabaseHas('task_tag', ['task_id' => $task->id, 'tag_id' => $tag->id]);
        $this->withToken($token)->getJson(route('api.v1.user'))->assertOk()->assertJsonPath('data.email_verified', true);
    }

    public function test_tampered_unsigned_and_expired_links_are_rejected(): void
    {
        $user = User::factory()->unverified()->create();
        $other = User::factory()->unverified()->create();
        $link = $this->link($user);
        foreach ([explode('?', $link)[0], $link.'&unexpected=1', str_replace('/'.$user->id.'/', '/'.$other->id.'/', $link)] as $invalid) {
            $this->get($invalid)->assertForbidden()->assertViewHas('emailVerification', ['verified' => false]);
        }
        $this->travel(61)->minutes();
        $this->get($link)->assertForbidden()->assertViewHas('emailVerification', ['verified' => false]);
        $this->assertFalse($user->fresh()->hasVerifiedEmail());
        $this->assertFalse($other->fresh()->hasVerifiedEmail());
    }

    public function test_links_for_a_previous_email_or_deleted_account_are_rejected(): void
    {
        $user = User::factory()->unverified()->create();
        $link = $this->link($user);
        $user->update(['email' => 'changed@example.test']);
        $this->get($link)->assertForbidden();
        $this->assertFalse($user->fresh()->hasVerifiedEmail());
        $newLink = $this->link($user);
        $user->delete();
        $this->get($newLink)->assertForbidden();
    }

    public function test_a_link_does_not_switch_or_confirm_the_browser_account(): void
    {
        $user = User::factory()->unverified()->create();
        $other = User::factory()->unverified()->create();
        $this->actingAs($other, 'web')->get($this->link($user))->assertOk();
        $this->assertAuthenticatedAs($other, 'web');
        $this->assertFalse($other->fresh()->hasVerifiedEmail());
        $this->assertTrue($user->fresh()->hasVerifiedEmail());
    }

    public function test_message_uses_portuguese_and_the_configured_host(): void
    {
        config(['app.url' => 'https://tarefas.example.test']);
        $user = User::factory()->unverified()->create();
        $this->actingAs($user, 'web')->postJson('https://untrusted.example.test/api/v1/email/verification-notification')->assertOk();
        $mail = Notification::sent($user, VerifyEmail::class)->sole()->toMail($user);
        $this->assertSame('Confirme seu e-mail — Tarefas', $mail->subject);
        $this->assertSame('Confirmar meu e-mail', $mail->actionText);
        $this->assertStringStartsWith('https://tarefas.example.test/email/verify/', $mail->actionUrl);
        $this->assertStringContainsString('60 minutos', implode(' ', $mail->outroLines));
        $this->get($mail->actionUrl)->assertOk();
    }

    public function test_browser_resend_requires_csrf(): void
    {
        $this->app->detectEnvironment(fn (): string => 'local');
        $user = User::factory()->unverified()->create();
        $this->actingAs($user, 'web')->withHeader('Origin', 'http://localhost')
            ->postJson(route('api.v1.verification.send'))->assertStatus(419);
        Notification::assertNothingSent();
    }

    public function test_mail_failure_is_retryable_on_resend(): void
    {
        Notification::shouldReceive('send')->once()->andThrow(new RuntimeException('SMTP indisponível no teste'));
        $user = User::factory()->unverified()->create();
        $this->actingAs($user, 'web')->postJson(route('api.v1.verification.send'))->assertStatus(503);
        $this->assertFalse($user->fresh()->hasVerifiedEmail());
    }
}
