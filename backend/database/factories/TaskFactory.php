<?php

namespace Database\Factories;

use App\Enums\Priority;
use App\Models\Task;
use App\Models\User;
use Illuminate\Database\Eloquent\Factories\Factory;

/**
 * @extends Factory<Task>
 */
class TaskFactory extends Factory
{
    /**
     * Define the model's default state.
     *
     * @return array<string, mixed>
     */
    public function definition(): array
    {
        $date = now()->format('Y-m-d');

        return [
            'user_id' => User::factory(),
            'title' => fake()->sentence(3),
            'description' => '',
            'date' => $date,
            'original_date' => fn (array $attributes): string => $attributes['date'],
            'deadline' => null,
            'priority' => Priority::Medium,
            'recurrence' => null,
            'series_id' => null,
            'completed' => false,
            'sync_version' => 0,
        ];
    }

    /**
     * Tarefa marcada para um dia específico (Y-m-d).
     */
    public function on(string $date): static
    {
        return $this->state(fn (array $attributes) => ['date' => $date, 'original_date' => $date]);
    }

    public function completed(): static
    {
        return $this->state(fn (array $attributes) => ['completed' => true]);
    }

    /**
     * Tarefa recorrente, como "DAILY" ou "WEEKLY:1,3,5".
     */
    public function recurring(string $rule = 'DAILY'): static
    {
        return $this->state(fn (array $attributes) => ['recurrence' => $rule]);
    }
}
