package org.succlz123.nrouter;

import android.app.Activity;
import android.os.Bundle;

import org.succlz123.nrouter.app.R;

@Path(path = "/app/activity/start_for_result")
public class StartForResultActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_second);

    }
}
