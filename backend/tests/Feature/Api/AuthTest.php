<?php

namespace Tests\Feature\Api;

use App\Models\User;
use Illuminate\Foundation\Testing\RefreshDatabase;
use Tests\TestCase;

class AuthTest extends TestCase
{
    use RefreshDatabase;

    public function test_register_creates_the_user_a_device_token_and_the_default_tags(): void
    {
        $response = $this->postJson(route('api.v1.register'), [
            'name' => 'Marcus',
            'email' => 'marcus@example.com',
            'password' => 'senha-segura-123',
            'password_confirmation' => 'senha-segura-123',
            'device_name' => 'Pixel 8',
        ]);

        $response->assertCreated()
            ->assertJsonStructure(['token', 'user' => ['id', 'name', 'email']])
            ->assertJsonPath('user.email', 'marcus@example.com');

        $user = User::where('email', 'marcus@example.com')->sole();
        $this->assertEqualsCanonicalizing(['Trabalho', 'Casa', 'Faculdade'], $user->tags()->pluck('name')->all());
        $this->assertSame('Pixel 8', $user->tokens()->sole()->name);

        $this->withToken($response->json('token'))
            ->getJson(route('api.v1.user'))
            ->assertOk()
            ->assertJsonPath('data.email', 'marcus@example.com');
    }

    public function test_register_rejects_an_email_already_in_use(): void
    {
        User::factory()->create(['email' => 'marcus@example.com']);

        $this->postJson(route('api.v1.register'), [
            'name' => 'Outra pessoa',
            'email' => 'MARCUS@example.com',
            'password' => 'senha-segura-123',
            'password_confirmation' => 'senha-segura-123',
            'device_name' => 'Pixel 8',
        ])->assertUnprocessable()->assertJsonValidationErrors('email');
    }

    public function test_login_returns_a_token_for_valid_credentials(): void
    {
        User::factory()->create(['email' => 'marcus@example.com', 'password' => 'senha-segura-123']);

        $this->postJson(route('api.v1.login'), [
            'email' => 'marcus@example.com',
            'password' => 'senha-segura-123',
            'device_name' => 'Navegador',
        ])->assertOk()->assertJsonStructure(['token', 'user' => ['id', 'name', 'email']]);
    }

    public function test_login_rejects_a_wrong_password(): void
    {
        User::factory()->create(['email' => 'marcus@example.com', 'password' => 'senha-segura-123']);

        $this->postJson(route('api.v1.login'), [
            'email' => 'marcus@example.com',
            'password' => 'senha-errada',
            'device_name' => 'Navegador',
        ])->assertUnprocessable()->assertJsonValidationErrors('email');
    }

    public function test_logout_revokes_only_the_current_device_token(): void
    {
        $user = User::factory()->create();
        $phoneToken = $user->createToken('Pixel 8')->plainTextToken;
        $browserToken = $user->createToken('Navegador')->plainTextToken;

        $this->withToken($phoneToken)->postJson(route('api.v1.logout'))->assertNoContent();
        $this->app['auth']->forgetGuards();

        $this->withToken($phoneToken)->getJson(route('api.v1.user'))->assertUnauthorized();
        $this->app['auth']->forgetGuards();
        $this->withToken($browserToken)->getJson(route('api.v1.user'))->assertOk();
    }

    public function test_the_api_requires_a_token(): void
    {
        $this->getJson(route('api.v1.tasks.index', ['search' => 'x']))->assertUnauthorized();
        $this->getJson(route('api.v1.tags.index'))->assertUnauthorized();
    }

    public function test_a_request_without_token_gets_401_even_without_asking_for_json(): void
    {
        $this->get(route('api.v1.tags.index'))
            ->assertUnauthorized()
            ->assertJsonPath('message', 'Unauthenticated.');
    }
}
