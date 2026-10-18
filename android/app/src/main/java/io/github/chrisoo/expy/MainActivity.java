package io.github.chrisoo.expy;

import android.os.Bundle;

import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(PayNotifPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
