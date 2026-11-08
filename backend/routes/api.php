<?php

use App\Http\Controllers\Api\V1\AuthController;
use App\Http\Controllers\Api\V1\PasswordController;
use App\Http\Controllers\Api\V1\SyncController;
use App\Http\Controllers\Api\V1\TagController;
use App\Http\Controllers\Api\V1\TaskController;
use Illuminate\Support\Facades\Route;

Route::prefix('v1')->name('api.v1.')->group(function () {
    Route::middleware('throttle:6,1')->group(function () {
        Route::post('register', [AuthController::class, 'register'])->name('register');
        Route::post('login', [AuthController::class, 'login'])->name('login');
        Route::post('forgot-password', [PasswordController::class, 'sendResetLink'])->name('password.email');
        Route::post('reset-password', [PasswordController::class, 'reset'])->name('password.update');
    });

    Route::middleware('auth:sanctum')->group(function () {
        Route::post('logout', [AuthController::class, 'logout'])->name('logout');
        Route::get('user', [AuthController::class, 'me'])->name('user');
        Route::post('sync', SyncController::class)->name('sync');

        Route::get('tasks/pending-counts', [TaskController::class, 'pendingCounts'])->name('tasks.pending-counts');
        Route::post('tasks/{task}/toggle', [TaskController::class, 'toggle'])->name('tasks.toggle');
        Route::apiResource('tasks', TaskController::class);
        Route::apiResource('tags', TagController::class);
    });
});
