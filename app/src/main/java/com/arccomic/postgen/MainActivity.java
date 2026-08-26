package com.arccomic.postgen;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import android.Manifest;
import android.util.Base64;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {
    private static final int REQ_FOLDER = 1001;
    private static final int REQ_PERMISSIONS = 1002;
    private static final int FILECHOOSER_RESULTCODE = 101;
    private static final String PREFS_NAME = "ArcPrefs";
    private static final String KEY_TREE_URI = "treeUri";
    private static final String KEY_FOLDER_NAME = "folderName";

    WebView webView;
    SharedPreferences prefs;
    Uri treeUri;
    ValueCallback<Uri[]> filePathCallback;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        webView = findViewById(R.id.webview);
        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setAllowFileAccess(true);
        ws.setAllowContentAccess(true);
        ws.setCacheMode(WebSettings.LOAD_NO_CACHE);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (MainActivity.this.filePathCallback != null) {
                    MainActivity.this.filePathCallback.onReceiveValue(null);
                }
                MainActivity.this.filePathCallback = filePathCallback;
                Intent intent = fileChooserParams.createIntent();
                try {
                    startActivityForResult(intent, FILECHOOSER_RESULTCODE);
                } catch (Exception e) {
                    MainActivity.this.filePathCallback = null;
                    return false;
                }
                return true;
            }
        });

        webView.addJavascriptInterface(new WebAppInterface(), "Android");
        webView.loadUrl("file:///android_asset/index.html");

        checkPermissions();

        String savedUri = prefs.getString(KEY_TREE_URI, null);
        if (savedUri == null) {
            webView.postDelayed(this::showFolderPicker, 1200);
        } else {
            treeUri = Uri.parse(savedUri);
        }
    }

    void checkPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            String[] perms = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE};
            boolean need = false;
            for (String p : perms) {
                if (ContextCompat.checkSelfPermission(this, p) != PackageManager.PERMISSION_GRANTED) need = true;
            }
            if (need) {
                ActivityCompat.requestPermissions(this, perms, REQ_PERMISSIONS);
            }
        }
    }

    void showFolderPicker() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
            | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        startActivityForResult(intent, REQ_FOLDER);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == FILECHOOSER_RESULTCODE) {
            Uri[] results = null;
            if (filePathCallback != null) {
                if (resultCode == Activity.RESULT_OK && data != null) {
                    String dataString = data.getDataString();
                    if (dataString != null) results = new Uri[]{Uri.parse(dataString)};
                }
                filePathCallback.onReceiveValue(results);
                filePathCallback = null;
            }
            return;
        }
        if (requestCode == REQ_FOLDER && resultCode == Activity.RESULT_OK && data != null) {
            treeUri = data.getData();
            if (treeUri != null) {
                int flags = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION;
                getContentResolver().takePersistableUriPermission(treeUri, flags);
                prefs.edit().putString(KEY_TREE_URI, treeUri.toString()).apply();
                DocumentFile df = DocumentFile.fromTreeUri(this, treeUri);
                String name = (df != null) ? df.getName() : "Selected folder";
                prefs.edit().putString(KEY_FOLDER_NAME, name).apply();
                webView.evaluateJavascript("if(window.onFolderPicked) onFolderPicked()", null);
                Toast.makeText(this, "Folder set: " + name, Toast.LENGTH_LONG).show();
            }
        }
    }

    DocumentFile getTargetDir() {
        if (treeUri == null) return null;
        return DocumentFile.fromTreeUri(this, treeUri);
    }

    @Override
    public void onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    public class WebAppInterface {
        @JavascriptInterface
        public String getSavePath() {
            return prefs.getString(KEY_FOLDER_NAME, "Not selected");
        }

        @JavascriptInterface
        public void showFolderPicker() {
            runOnUiThread(MainActivity.this::showFolderPicker);
        }

        @JavascriptInterface
        public void toast(String msg) {
            runOnUiThread(() -> Toast.makeText(MainActivity.this, msg, Toast.LENGTH_SHORT).show());
        }

        @JavascriptInterface
        public void saveFile(String filename, String mimeType, String base64Data) {
            try {
                DocumentFile dir = getTargetDir();
                if (dir == null) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Choose a folder first!", Toast.LENGTH_LONG).show();
                        webView.evaluateJavascript("if(window.promptFolder) promptFolder()", null);
                    });
                    return;
                }
                DocumentFile existing = dir.findFile(filename);
                if (existing != null) existing.delete();
                DocumentFile file = dir.createFile(mimeType, filename);
                if (file == null) throw new Exception("Create failed");
                byte[] bytes = Base64.decode(base64Data, Base64.DEFAULT);
                try (OutputStream out = getContentResolver().openOutputStream(file.getUri())) {
                    if (out != null) out.write(bytes);
                }
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Saved: " + filename, Toast.LENGTH_SHORT).show());
            } catch (Exception e) {
                runOnUiThread(() -> Toast.makeText(MainActivity.this, "Save error: " + e.getMessage(), Toast.LENGTH_LONG).show());
            }
        }

        @JavascriptInterface
        public String loadFile(String filename) {
            try {
                DocumentFile dir = getTargetDir();
                if (dir == null) return "";
                DocumentFile file = dir.findFile(filename);
                if (file == null) return "";
                StringBuilder sb = new StringBuilder();
                try (InputStream in = getContentResolver().openInputStream(file.getUri())) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                    }
                }
                return sb.toString();
            } catch (Exception e) {
                return "";
            }
        }

        @JavascriptInterface
        public void autoSave(String jsonData) {
            try {
                DocumentFile dir = getTargetDir();
                if (dir == null) return;
                DocumentFile existing = dir.findFile("autosave.json");
                if (existing != null) existing.delete();
                DocumentFile file = dir.createFile("application/json", "autosave.json");
                if (file == null) return;
                try (OutputStream out = getContentResolver().openOutputStream(file.getUri())) {
                    if (out != null) out.write(jsonData.getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception e) { /* silent */ }
        }

        @JavascriptInterface
        public String loadAutoSave() {
            try {
                DocumentFile dir = getTargetDir();
                if (dir == null) return "";
                DocumentFile file = dir.findFile("autosave.json");
                if (file == null) return "";
                StringBuilder sb = new StringBuilder();
                try (InputStream in = getContentResolver().openInputStream(file.getUri())) {
                    byte[] buf = new byte[4096];
                    int n;
                    while ((n = in.read(buf)) != -1) {
                        sb.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                    }
                }
                return sb.toString();
            } catch (Exception e) { return ""; }
        }

        @JavascriptInterface
        public String listFiles() {
            try {
                DocumentFile dir = getTargetDir();
                if (dir == null) return "[]";
                StringBuilder sb = new StringBuilder("[");
                boolean first = true;
                for (DocumentFile f : dir.listFiles()) {
                    if (f != null && f.isFile()) {
                        String name = f.getName();
                        if (name.endsWith(".html") || name.endsWith(".json")) {
                            if (!first) sb.append(",");
                            first = false;
                            sb.append("\"").append(name.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                        }
                    }
                }
                sb.append("]");
                return sb.toString();
            } catch (Exception e) { return "[]"; }
        }
    }
}
