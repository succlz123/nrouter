package org.succlz123.nrouter;

import android.app.Application;

public class MainApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        NRouter.init(this);
    }
}
