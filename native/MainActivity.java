package com.chaman.assistant;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AppOpenerPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
