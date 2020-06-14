package org.succlz123.nrouter;

import android.os.Bundle;
import android.widget.TextView;

import org.succlz123.nrouter.app.R;
import org.succlz123.nrouter.app.test.TestToastService;

@Path(path = "/app/activity/first")
public class FirstActivity extends BaseActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        String str = getIntent().getStringExtra("params");
        ((TextView) findViewById(R.id.content)).setText("Go from Main Activity " + str);
        ((TextView) findViewById(R.id.content)).append("\nClick to show app toast and goto second activity");
        findViewById(R.id.content).setOnClickListener(v -> {
            ToastService toastService = NRouter.path("/app/service/toast").open();
            toastService.show(FirstActivity.this);
            NRouter.path("/app_test/activity/second").with("params", "I am from Fist Activity").open();
        });
        ((TextView) findViewById(R.id.content_2)).setText("\nClick to show app_test toast");
        findViewById(R.id.content_2).setOnClickListener(v -> {
            TestToastService toastService = NRouter.path("/app_test/service/toast").open();
            toastService.show(FirstActivity.this);
        });
    }
}
