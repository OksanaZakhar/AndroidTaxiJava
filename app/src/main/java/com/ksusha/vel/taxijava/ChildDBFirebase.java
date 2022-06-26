package com.ksusha.vel.taxijava;

public enum ChildDBFirebase {

    DRIVER("driver"),
    CLIENT("client");


    private String title;

    ChildDBFirebase(String title) {
        this.title = title;
    }

    public String getTitle() {
        return title;
    }
}
