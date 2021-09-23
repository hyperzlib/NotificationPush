package com.RichardLuo.notificationpush;

import static android.app.NotificationManager.IMPORTANCE_DEFAULT;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.webkit.CookieManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QQLogin extends AppCompatActivity {
    WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // testNotify();
        super.onCreate(savedInstanceState);
        setTheme(getSharedPreferences("MainActivity", MODE_PRIVATE).getInt("style", R.style.base_DayNight_AppTheme_teal));
        setContentView(R.layout.qq_login);
        Objects.requireNonNull(getSupportActionBar()).setDisplayHomeAsUpEnabled(true);

        web = findViewById(R.id.web);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return false;
            }
        });
        web.getSettings().setJavaScriptEnabled(true);
        if (CookieManager.getInstance().getCookie("https://qun.qq.com") != null)
            web.loadUrl("https://qun.qq.com/member.html");
        else
            web.loadUrl("https://xui.ptlogin2.qq.com/cgi-bin/xlogin?pt_disable_pwd=0&appid=715030901&daid=73&hide_close_icon=1&pt_no_auth=1&s_url=https%3A%2F%2Fqun.qq.com%2Fmember.html%23gid%3D8292362");
    }

    public void testNotify() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            NotificationChannel mChannel = new NotificationChannel("CHANNEL_ID_SHOW_BADGE", "CHANNEL_ID_SHOW_BADGE", IMPORTANCE_DEFAULT);
            notificationManager.createNotificationChannel(mChannel);

            Icon ico = Icon.createWithResource(this, R.drawable.ic_notification);
            try {
                ico = Icon.createWithBitmap(((BitmapDrawable) getPackageManager().getApplicationIcon("com.tencent.mobileqq")).getBitmap());
            } catch (PackageManager.NameNotFoundException e) {
                e.printStackTrace();
            } catch (Exception e) {
                Log.e("FCMReceiver", "Cannot get icon bitmap", e);
            }

            Bundle bundle = new Bundle();
            bundle.putString("forward_channelId", "CHANNEL_ID_SHOW_BADGE");
            bundle.putString("forward_group", "default");
            Notification notification = new Notification.Builder(this, "CHANNEL_ID_SHOW_BADGE")
                    .setSmallIcon(ico)
                    .setContentTitle("测试")
                    .setContentText("测试伪装")
                    .setAutoCancel(true)
                    .setGroup("com.tencent.mobileqq")
                    .setExtras(bundle)
                    .build();
            try {
                notificationManager.notify(123456, notification);
            } catch (Exception e) {
                Log.e("GCMForwarding", "test send error: ", e);
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.webmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.clear_cookies:
                CookieManager.getInstance().removeAllCookies(null);
                Toast.makeText(this, "清除完成", Toast.LENGTH_SHORT).show();
                break;
            case R.id.refresh:
                web.reload();
                break;
            case android.R.id.home:
                finish();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void finish() {
        Intent intent = new Intent();
        String cookies = CookieManager.getInstance().getCookie("https://qun.qq.com") + ";";
        Matcher matcher_pskey = Pattern.compile("p_skey=(.*?);").matcher(cookies);
        Matcher matcher_skey = Pattern.compile("skey=(.*?);").matcher(cookies);
        Matcher matcher_uin = Pattern.compile("p_uin=(.*?);").matcher(cookies);
        Matcher matcher_token = Pattern.compile("pt4_token=(.*?);").matcher(cookies);
        if (matcher_pskey.find() && matcher_skey.find() && matcher_uin.find() && matcher_token.find()) {
            intent.putExtra("pskey", matcher_pskey.group(1));
            intent.putExtra("skey", matcher_skey.group(1));
            intent.putExtra("uin", matcher_uin.group(1));
            intent.putExtra("token", matcher_token.group(1));
            setResult(RESULT_OK, intent);
        } else
            setResult(0);
        super.finish();
    }
}
