<?php

use App\Http\Controllers\Api\V1\PasswordController;
use App\Http\Controllers\Web\AuthController;
use Illuminate\Support\Facades\Route;

Route::view('/', 'app')->name('home');
Route::get('forgot-password', [PasswordController::class, 'show'])->name('password.request');
Route::get('reset-password/{token}', [PasswordController::class, 'show'])->name('password.reset');

Route::middleware('throttle:6,1')->group(function () {
    Route::post('forgot-password', [PasswordController::class, 'sendResetLink'])->name('password.email');
    Route::post('reset-password', [PasswordController::class, 'reset'])->name('password.update');
});

Route::middleware('throttle:6,1')->group(function () {
    Route::post('login', [AuthController::class, 'login'])->name('login');
    Route::post('register', [AuthController::class, 'register'])->name('register');
});

Route::post('logout', [AuthController::class, 'logout'])->middleware('auth:web')->name('logout');
