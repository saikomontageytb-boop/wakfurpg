package fr.wakfu.rpg.core;

/** Public dungeon details sent to the client preview screen. */
public record DungeonInfo(String id, String name, int minLevel, int maxLevel) { }
