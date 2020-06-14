package org.succlz123.nrouter;

import android.app.Activity;
import android.widget.Toast;

@Path(path = "/app/service/player")
public class PlayerService {

    void play(Activity activity) {
        Toast.makeText(activity, "player is play", Toast.LENGTH_SHORT).show();
    }
}
