<?php

namespace Tests\Feature\Web;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class SessionAuthTest extends TestCase
{
    use RefreshDatabase;

    public function test_the_home_page_serves_the_vue_app_and_named_endpoints(): void
    {
        $this->withoutVite();

        $response = $this->get(route('home'))->assertOk()->assertViewIs('app')
            ->assertSee('lang="pt-BR"', false)
            ->assertSee('id="app"', false)
            ->assertSee('id="app-config"', false);

        preg_match('/<script id="app-config"[^>]*>(.*?)<\/script>/s', $response->getContent(), $matches);
        $endpoints = json_decode($matches[1], true, flags: JSON_THROW_ON_ERROR);
        $this->assertSame(route('api.v1.tasks.index', absolute: false), $endpoints['tasks']);
        $this->assertSame(route('login', absolute: false), $endpoints['login']);
    }

    public function test_register_creates_a_session_and_default_tags_without_a_device_token(): void
    {
        $this->postJson(route('register'), [
            'name' => 'Marcus', 'email' => 'marcus@example.com',
            'password' => 'senha-segura-123', 'password_confirmation' => 'senha-segura-123',
        ])->assertCreated()->assertJsonPath('data.email', 'marcus@example.com')->assertJsonMissingPath('token');

        $user = User::where('email', 'marcus@example.com')->sole();
        $this->assertAuthenticatedAs($user, 'web');
        $this->assertSame(0, $user->tokens()->count());
        $this->assertEqualsCanonicalizing(['Trabalho', 'Casa', 'Faculdade'], $user->tags()->pluck('name')->all());
    }

    public function test_web_login_authenticates_stateful_requests_to_the_existing_api(): void
    {
        $user = User::factory()->create(['password' => 'senha-segura-123']);

        $this->postJson(route('login'), ['email' => $user->email, 'password' => 'senha-segura-123'])
            ->assertOk()->assertJsonPath('data.id', $user->id);

        $this->app['auth']->forgetGuards();
        $this->withHeader('Origin', 'http://localhost')->getJson(route('api.v1.user'))
            ->assertOk()->assertJsonPath('data.id', $user->id);

        $this->postJson(route('api.v1.tasks.store'), ['title' => 'Criada pelo web', 'date' => '2026-11-10'])
            ->assertCreated();
        $this->assertSame(1, $user->tasks()->count());
        $this->assertSame(0, $user->tokens()->count());
    }

    public function test_wrong_credentials_do_not_authenticate_the_browser(): void
    {
        $user = User::factory()->create(['password' => 'senha-segura-123']);

        $this->postJson(route('login'), ['email' => $user->email, 'password' => 'incorreta'])
            ->assertUnprocessable()->assertJsonValidationErrors('email')
            ->assertJsonPath('errors.email.0', 'E-mail ou senha incorretos.');
        $this->assertGuest('web');
    }

    public function test_register_rejects_duplicate_emails_and_mismatched_passwords(): void
    {
        $user = User::factory()->create();

        $this->postJson(route('register'), [
            'name' => 'Outra conta', 'email' => $user->email,
            'password' => 'senha-segura-123', 'password_confirmation' => 'outra-senha-123',
        ])->assertUnprocessable()->assertJsonValidationErrors(['email', 'password']);
        $this->assertGuest('web');
    }

    public function test_web_logout_ends_the_session_and_preserves_the_phone_token(): void
    {
        $user = User::factory()->create();
        $token = $user->createToken('Android')->plainTextToken;

        $this->actingAs($user, 'web')->postJson(route('logout'))->assertNoContent();
        $this->assertGuest('web');
        $this->app['auth']->forgetGuards();
        $this->withHeader('Origin', 'http://localhost')->getJson(route('api.v1.user'))->assertUnauthorized();
        $this->app['auth']->forgetGuards();
        $this->withToken($token)->getJson(route('api.v1.user'))->assertOk();
    }

    public function test_api_logout_also_accepts_a_browser_session_without_deleting_device_tokens(): void
    {
        $user = User::factory()->create();
        $user->createToken('Android');

        $this->actingAs($user, 'web')->withHeader('Origin', 'http://localhost')
            ->postJson(route('api.v1.logout'))->assertNoContent();

        $this->assertGuest('web');
        $this->assertSame(1, $user->tokens()->count());
    }

    public function test_web_login_and_stateful_api_writes_require_csrf_protection(): void
    {
        $this->app->detectEnvironment(fn (): string => 'local');

        $this->postJson(route('login'), ['email' => 'marcus@example.com', 'password' => 'senha'])
            ->assertStatus(419);

        $this->actingAs(User::factory()->create(), 'web')->withHeader('Origin', 'http://localhost')
            ->postJson(route('api.v1.tasks.store'), ['title' => 'Sem CSRF', 'date' => '2026-11-10'])
            ->assertStatus(419);
    }

    public function test_the_browser_can_request_a_csrf_cookie(): void
    {
        $this->get(route('sanctum.csrf-cookie'))->assertNoContent()->assertCookie('XSRF-TOKEN');
    }

    public function test_web_login_is_rate_limited(): void
    {
        for ($attempt = 0; $attempt < 6; $attempt++) {
            $this->postJson(route('login'), ['email' => 'marcus@example.com', 'password' => 'incorreta'])
                ->assertUnprocessable();
        }

        $this->postJson(route('login'), ['email' => 'marcus@example.com', 'password' => 'incorreta'])
            ->assertTooManyRequests();
    }
}
