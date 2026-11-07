<?php

use App\Http\Controllers\Web\AuthController;
use Illuminate\Support\Facades\Route;

Route::view('/', 'app')->name('home');

Route::middleware('throttle:6,1')->group(function () {
    Route::post('login', [AuthController::class, 'login'])->name('login');
    Route::post('register', [AuthController::class, 'register'])->name('register');
});

Route::post('logout', [AuthController::class, 'logout'])->middleware('auth:web')->name('logout');
