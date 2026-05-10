package com.talhanation.bannermod.commands.admin;

import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSourceStack;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;

class AdminDebugCommandsTest {
    @Test
    void economyObjectiveCommandsAreRegistered() {
        CommandNode<CommandSourceStack> root = AdminDebugCommands.debug().build();

        CommandNode<CommandSourceStack> objectives = child(child(root, "economy"), "objectives");
        assertNotNull(child(objectives, "mine-dispute"));
        assertNotNull(child(objectives, "blockade"));
        assertNotNull(child(objectives, "resolve"));
        assertNotNull(child(objectives, "prune"));
    }

    private static CommandNode<CommandSourceStack> child(CommandNode<CommandSourceStack> parent, String name) {
        CommandNode<CommandSourceStack> child = parent.getChild(name);
        assertNotNull(child, name);
        return child;
    }
}
