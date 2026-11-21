package com.example.info2ai.staticworld;

import com.example.info2ai.events.EventData;
import com.example.info2ai.model.Facts;

/** Stable diagnostics for generation calculated with the game's loaded inputs. */
public final class GenerationTrust {
    private GenerationTrust() { }

    public static Facts builtinFacts() {
        return EventData.of("trusted", true, "reason", "builtin_game_generation");
    }
}
