<?php

namespace App\Http\Controllers\Api\V1;

use App\Actions\Tags\CreateDefaultTags;
use App\Http\Controllers\Controller;
use App\Http\Requests\Api\V1\LoginRequest;
use App\Http\Requests\Api\V1\RegisterRequest;
use App\Http\Resources\UserResource;
use App\Models\User;
use Illuminate\Auth\Events\Registered;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\DB;
use Illuminate\Support\Facades\Hash;
use Illuminate\Validation\ValidationException;
use Laravel\Sanctum\PersonalAccessToken;

/**
 * Login por token (Sanctum): cada aparelho recebe o seu, com o nome do aparelho.
 */
class AuthController extends Controller
{
    public function register(RegisterRequest $request, CreateDefaultTags $createDefaultTags): JsonResponse
    {
        $user = DB::transaction(function () use ($request, $createDefaultTags): User {
            $user = User::create($request->safe()->only(['name', 'email', 'password']));
            $createDefaultTags->handle($user);

            return $user;
        });

        event(new Registered($user));

        return $this->tokenResponse($user, $request->validated('device_name'), Response::HTTP_CREATED);
    }

    public function login(LoginRequest $request): JsonResponse
    {
        $user = User::where('email', $request->validated('email'))->first();

        if ($user === null || ! Hash::check($request->validated('password'), $user->password)) {
            throw ValidationException::withMessages(['email' => __('auth.failed')]);
        }

        return $this->tokenResponse($user, $request->validated('device_name'));
    }

    /**
     * Encerra só a sessão deste aparelho; os outros continuam logados.
     */
    public function logout(Request $request): Response
    {
        $token = $request->user()->currentAccessToken();

        if ($token instanceof PersonalAccessToken) {
            $token->delete();
        } else {
            Auth::guard('web')->logout();
            $request->session()->invalidate();
            $request->session()->regenerateToken();
        }

        return response()->noContent();
    }

    public function me(Request $request): UserResource
    {
        return new UserResource($request->user());
    }

    private function tokenResponse(User $user, string $deviceName, int $status = Response::HTTP_OK): JsonResponse
    {
        return response()->json([
            'token' => $user->createToken($deviceName)->plainTextToken,
            'user' => new UserResource($user),
        ], $status);
    }
}
