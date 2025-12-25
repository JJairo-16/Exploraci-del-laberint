package com.jairo.items;

public enum Qualities {
    COMMON(120, 200, 255),
    EPIC(175, 95, 255),
    LEGENDARY(255, 210, 90),
    UNIQUE(210, 40, 40);

    public final int red;
    public final int green;
    public final int blue;

    private Qualities(int r, int g, int b) {
        red = r;
        green = g;
        blue = b;
    }
}
