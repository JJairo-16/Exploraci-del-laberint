package com.jairo.utils;

public class PositionHud {
    private final int boardW;
    private final int boardH;

    public PositionHud(int boardW, int boardH) {
        this.boardW = boardW;
        this.boardH = boardH;
    }

    public int getX(int x) {
        int maxW = boardW - 2;
        

        return x - maxW / 2;
    }

    public int getY(int y) {
        int maxH = boardH - 2;

        return -(y - maxH / 2);
    }
}
