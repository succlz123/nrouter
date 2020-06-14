package org.succlz123.nrouter;

import android.app.Activity;
import android.widget.Toast;

@Path(path = "/app/service/toast")
public class ToastService {

    void show(Activity activity) {
        Toast.makeText(activity, "321", Toast.LENGTH_SHORT).show();
    }
}
