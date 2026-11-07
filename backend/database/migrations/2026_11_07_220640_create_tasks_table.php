<?php

use Illuminate\Database\Migrations\Migration;
use Illuminate\Database\Schema\Blueprint;
use Illuminate\Support\Facades\Schema;

return new class extends Migration
{
    /**
     * Mesmos campos da tabela tasks do app (banco versão 5).
     */
    public function up(): void
    {
        Schema::create('tasks', function (Blueprint $table) {
            $table->uuid('id')->primary();
            $table->foreignId('user_id')->constrained()->cascadeOnDelete();
            $table->string('title');
            $table->text('description');
            $table->date('date');
            $table->date('original_date');
            $table->date('deadline')->nullable();
            $table->string('priority', 10);
            $table->string('recurrence', 50)->nullable();
            $table->uuid('series_id')->nullable()->index();
            $table->boolean('completed')->default(false);
            $table->unsignedBigInteger('sync_version');
            $table->timestamps(precision: 3);
            $table->softDeletes(precision: 3);

            $table->index(['user_id', 'date']);
            $table->index(['user_id', 'sync_version']);
        });
    }

    /**
     * Reverse the migrations.
     */
    public function down(): void
    {
        Schema::dropIfExists('tasks');
    }
};
