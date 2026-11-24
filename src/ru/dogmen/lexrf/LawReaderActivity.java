package ru.dogmen.lexrf;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public final class LawReaderActivity extends Activity {
    static final String TITLE = "title";
    static final String URL = "url";
    static final String TERM = "term";
    static final String ZOOM = "zoom";
    static final String LIGHT = "light";

    private WebView reader;
    private EditText search;
    private TextView status;
    private String contentsUrl;
    private boolean pageReady;
    private boolean loadFailed;

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        Intent intent = getIntent();
        contentsUrl = intent.getStringExtra(URL);
        if (!isDocument(contentsUrl)) {
            finish();
            return;
        }

        boolean light = intent.getBooleanExtra(LIGHT, false);
        int background = Color.parseColor(light ? "#F0F2F8" : "#10131D");
        int foreground = Color.parseColor(light ? "#152037" : "#F2F5FF");
        int secondary = Color.parseColor(light ? "#526077" : "#AAB5CE");
        getWindow().setStatusBarColor(background);
        getWindow().setNavigationBarColor(background);
        if (light) getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR | View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setBackgroundColor(background);
        setContentView(layout);

        LinearLayout header = row(layout);
        header.addView(button("‹", "Назад", this::onBackPressed));
        TextView title = new TextView(this);
        title.setText(intent.getStringExtra(TITLE));
        title.setTextColor(foreground);
        title.setTextSize(19);
        title.setPadding(dp(8), 0, dp(8), 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(button("Оглавление", "Открыть оглавление", () -> reader.loadUrl(contentsUrl)));

        LinearLayout tools = row(layout);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(foreground);
        search.setHintTextColor(secondary);
        search.setTextSize(15);
        search.setHint("Номер или слово на странице");
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setText(saved == null ? intent.getStringExtra(TERM) : saved.getString(TERM));
        tools.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
        tools.addView(button("↑", "Предыдущее совпадение", () -> reader.findNext(false)));
        tools.addView(button("↓", "Следующее совпадение", () -> reader.findNext(true)));
        search.setOnEditorActionListener((view, action, event) -> {
            if (action != EditorInfo.IME_ACTION_SEARCH) return false;
            ((InputMethodManager)getSystemService(INPUT_METHOD_SERVICE))
                    .hideSoftInputFromWindow(search.getWindowToken(), 0);
            search.clearFocus();
            findOnPage();
            return true;
        });

        LinearLayout source = row(layout);
        status = new TextView(this);
        status.setTextColor(secondary);
        status.setTextSize(13);
        status.setPadding(dp(8), 0, dp(4), 0);
        source.addView(status, new LinearLayout.LayoutParams(0, -2, 1));
        source.addView(button("В браузере", "Открыть страницу в браузере", this::openBrowser));

        reader = new WebView(this);
        reader.getSettings().setAllowFileAccess(false);
        reader.getSettings().setAllowContentAccess(false);
        reader.getSettings().setTextZoom(intent.getIntExtra(ZOOM, 100));
        reader.getSettings().setBuiltInZoomControls(true);
        reader.getSettings().setDisplayZoomControls(false);
        if (Build.VERSION.SDK_INT >= 33) reader.getSettings().setAlgorithmicDarkeningAllowed(!light);
        layout.addView(reader, new LinearLayout.LayoutParams(-1, 0, 1));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { findOnPage(); }
            @Override public void afterTextChanged(Editable s) { }
        });
        reader.setFindListener((index, count, done) -> {
            if (!done || !pageReady || search.getText().toString().trim().isEmpty()) return;
            status.setText(count == 0 ? "На этой странице совпадений нет" : "Совпадение " + (index + 1) + " из " + count);
        });
        reader.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isDocument(request.getUrl().toString());
            }

            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                pageReady = false;
                loadFailed = false;
                status.setText("Загрузка документа…");
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (loadFailed || !isDocument(url)) return;
                pageReady = true;
                findOnPage();
            }

            @Override public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) showLoadFailure();
            }

            @Override public void onReceivedHttpError(WebView view, WebResourceRequest request, WebResourceResponse error) {
                if (request.isForMainFrame()) showLoadFailure();
            }
        });

        if (saved == null || reader.restoreState(saved) == null) reader.loadUrl(contentsUrl);
        layout.setFocusableInTouchMode(true);
        layout.requestFocus();
    }

    private boolean isDocument(String value) {
        if (value == null) return false;
        Uri uri = Uri.parse(value);
        String host = uri.getHost();
        String path = uri.getPath();
        return "https".equalsIgnoreCase(uri.getScheme())
                && ("www.consultant.ru".equalsIgnoreCase(host) || "consultant.ru".equalsIgnoreCase(host))
                && path != null && path.startsWith("/document/");
    }

    private void showLoadFailure() {
        pageReady = false;
        loadFailed = true;
        status.setText("Не удалось загрузить. Повторите или откройте в браузере.");
    }

    private void findOnPage() {
        if (!pageReady) return;
        String term = search.getText().toString().trim();
        if (term.isEmpty()) {
            reader.clearMatches();
            status.setText("Источник · КонсультантПлюс");
        } else {
            reader.findAllAsync(term);
        }
    }

    private void openBrowser() {
        String url = reader.getUrl();
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(isDocument(url) ? url : contentsUrl)));
        } catch (android.content.ActivityNotFoundException e) {
            Toast.makeText(this, "Браузер недоступен", Toast.LENGTH_SHORT).show();
        }
    }

    private LinearLayout row(LinearLayout parent) {
        LinearLayout row = new LinearLayout(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(6), 0, dp(6), 0);
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private Button button(String label, String description, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setContentDescription(description);
        button.setAllCaps(false);
        button.setMinWidth(dp(42));
        button.setMinimumWidth(dp(42));
        button.setPadding(dp(8), 0, dp(8), 0);
        button.setOnClickListener(view -> action.run());
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    @Override protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        if (reader != null) reader.saveState(outState);
        if (search != null) outState.putString(TERM, search.getText().toString());
    }

    @Override public void onBackPressed() {
        if (reader != null && reader.canGoBack()) reader.goBack();
        else finish();
    }

    @Override protected void onDestroy() {
        if (reader != null) {
            ((LinearLayout)reader.getParent()).removeView(reader);
            reader.destroy();
        }
        super.onDestroy();
    }
}
