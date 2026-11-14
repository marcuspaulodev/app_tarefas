<?php

namespace Tests\Unit;

use App\Models\Task;
use App\Support\Checklist;
use App\Support\OccurrenceId;
use App\Support\RecurrenceRule;
use Tests\TestCase;

/**
 * Roda os casos de shared/fixtures/rules.json, os mesmos que o app Android roda.
 * Se este teste falhar, as regras do servidor e do app divergiram.
 */
class SharedRulesTest extends TestCase
{
    /**
     * @return array<string, mixed>
     */
    private function fixtures(): array
    {
        $path = dirname(base_path()).'/shared/fixtures/rules.json';

        return json_decode(file_get_contents($path), true, flags: JSON_THROW_ON_ERROR);
    }

    public function test_next_occurrence(): void
    {
        foreach ($this->fixtures()['next_occurrence'] as $case) {
            $this->assertSame(
                $case['next'],
                RecurrenceRule::decode($case['rule'])->nextOccurrenceAfter($case['date']),
                json_encode($case),
            );
        }
    }

    public function test_invalid_rules(): void
    {
        foreach ($this->fixtures()['invalid_rules'] as $raw) {
            $this->assertNull(RecurrenceRule::decode($raw), json_encode($raw));
        }
    }

    public function test_occurrence_id(): void
    {
        foreach ($this->fixtures()['occurrence_id'] as $case) {
            $this->assertSame($case['id'], OccurrenceId::for($case['series_id'], $case['date']), json_encode($case));
        }
    }

    public function test_shown_on(): void
    {
        foreach ($this->fixtures()['shown_on'] as $case) {
            $task = new Task(['date' => $case['date'], 'completed' => $case['completed']]);

            $this->assertSame($case['shown_on'], $task->shownOn($case['today']), json_encode($case));
        }
    }

    public function test_next_checklist(): void
    {
        foreach ($this->fixtures()['next_checklist'] as $case) {
            $this->assertSame($case['next'], Checklist::forNextOccurrence($case['checklist']), json_encode($case));
        }
    }
}
