<?php

namespace App\Http\Controllers\Web;

use App\Actions\Tags\CreateDefaultTags;
use App\Http\Controllers\Controller;
use App\Http\Requests\Web\LoginRequest;
use App\Http\Requests\Web\RegisterRequest;
use App\Http\Resources\UserResource;
use App\Models\User;
use Illuminate\Http\JsonResponse;
use Illuminate\Http\Request;
use Illuminate\Http\Response;
use Illuminate\Support\Facades\Auth;
use Illuminate\Support\Facades\DB;
use Illuminate\Validation\ValidationException;

class AuthController extends Controller
{
    public function login(LoginRequest $request): UserResource
    {
        if (! Auth::guard('web')->attempt($request->safe()->only(['email', 'password']))) {
            throw ValidationException::withMessages(['email' => 'E-mail ou senha incorretos.']);
        }

        $request->session()->regenerate();

        return new UserResource($request->user());
    }

    public function register(RegisterRequest $request, CreateDefaultTags $createDefaultTags): JsonResponse
    {
        $user = DB::transaction(function () use ($request, $createDefaultTags): User {
            $user = User::create($request->safe()->only(['name', 'email', 'password']));
            $createDefaultTags->handle($user);

            return $user;
        });

        Auth::guard('web')->login($user);
        $request->session()->regenerate();

        return (new UserResource($user))->response()->setStatusCode(Response::HTTP_CREATED);
    }

    public function logout(Request $request): Response
    {
        Auth::guard('web')->logout();
        $request->session()->invalidate();
        $request->session()->regenerateToken();

        return response()->noContent();
    }
}
