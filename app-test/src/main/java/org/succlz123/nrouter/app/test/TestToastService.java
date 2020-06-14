package org.succlz123.nrouter.app.test;

import android.app.Activity;
import android.widget.Toast;

import org.succlz123.nrouter.Path;

@Path(path = "/app_test/service/toast")
public class TestToastService {

    public void show(Activity activity) {
        Toast.makeText(activity, "123_test", Toast.LENGTH_SHORT).show();
    }
}
