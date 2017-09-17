package eecs.arlocation;

import android.app.Activity;
import android.content.Intent;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

public class CircleActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_circle);
        findViewById(R.id.menux).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                startActivity(new Intent(CircleActivity.this, FullscreenActivity.class));
                finish();
            }
        });

    }


}
