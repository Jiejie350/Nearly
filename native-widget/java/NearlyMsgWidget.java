package com.benjamin.nearly; // CHANGE to your app's package

/** Messages widget: one widget, text or handwriting, no modes. Checks every 10 min (see NearlyMsgAlarm). */
public class NearlyMsgWidget extends NearlyBaseProvider {
    @Override
    String type() { return "msg"; }
}
