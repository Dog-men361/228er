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
import android.widget.ListView;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONTokener;

public final class LawReaderActivity extends Activity {
    static final String TITLE = "title";
    static final String URL = "url";
    static final String TERM = "term";
    static final String ZOOM = "zoom";
    static final String LIGHT = "light";

    private WebView reader;
    private EditText search;
    private TextView status;
    private TextView articleHeading;
    private Button viewMode;
    private ListView list;
    private final ArrayList<Entry> entries = new ArrayList<>();
    private final ArrayList<Entry> filtered = new ArrayList<>();
    private final HashMap<String, String> articleTitles = new HashMap<>();
    private BaseAdapter adapter;
    private String lawName;
    private int foreground;
    private int secondary;
    private int textZoom;
    private int loadGeneration;
    private String pendingArticle;
    private final java.util.HashSet<String> followed = new java.util.HashSet<>();
    private String contentsUrl;
    private boolean pageReady;
    private boolean loadFailed;

    private static final Pattern ARTICLE = Pattern.compile("(?iu)(?:^|\\s)Статья\\s+(\\d+(?:[.\u2010-\u2015\\-]\\d+)*)(?:[.\\s]+|$)(.*)");
    private static final Pattern GROUP = Pattern.compile("(?iu)(?:^|\\s)(?:Раздел|Подраздел|Глава|Часть|Параграф)\\s+(?:[IVXLCDM]+|[0-9]+|[а-яё]+)(?:\\b|\\.)|^§\\s*\\d+");
    private static final String INSPECT_PAGE = "(function(){"
            + "var clean=function(s){return (s||'').replace(/\\s+/g,' ').trim()};"
            + "var heading=Array.from(document.querySelectorAll('h1,h2')).map(function(e){return clean(e.innerText)});"
            + "var address=function(e){try{return new URL(e.getAttribute('href')||e.dataset.href,location.href).href}catch(err){return ''}};"
            + "var article=/^Статья\\s+\\d/i, group=/^(?:Раздел|Подраздел|Глава|Часть|Параграф)\\s|^§\\s*\\d/i;"
            + "var sameLaw=function(url){try{return new URL(url).pathname.toLowerCase().split('/')[2]===location.pathname.toLowerCase().split('/')[2]}catch(err){return false}};"
            + "var selector='a[href],[data-href],li,span,div,p,td';"
            + "var links=[], lastGroup='';"
            + "document.querySelectorAll(selector).forEach(function(e){"
            + "if(e.closest('footer,script,style'))return;"
            + "var linked=e.matches('a[href],[data-href]');"
            + "if(!linked&&e.closest('a[href],[data-href]'))return;"
            + "var caption=clean(linked?e.textContent:Array.from(e.childNodes).filter(function(n){return n.nodeType===3||/^(SPAN|B|STRONG|I|EM|NOBR|SUP)$/.test(n.nodeName)}).map(function(n){return n.textContent}).join(' '));"
            + "if(!caption||caption.length>900||!(article.test(caption)||group.test(caption)))return;"
            + "var url=linked?address(e):'';"
            + "if(linked&&sameLaw(url)&&new URL(url).pathname!==location.pathname&&group.test(caption))lastGroup=url;"
            + "if(linked){if(sameLaw(url))links.push([caption,url,false]);return;}"
            + "if(article.test(caption)){var nearest='';"
            + "for(var parent=e.closest('li');parent;parent=parent.parentElement.closest('li')){"
            + "var anchor=Array.from(parent.children).find(function(a){return a.matches('a[href],[data-href]')&&group.test(clean(a.textContent))&&sameLaw(address(a))});"
            + "if(anchor){nearest=address(anchor);break;}}"
            + "links.push([caption,nearest||lastGroup||location.href,true]);}});"
            + "return JSON.stringify({heading:heading,links:links.filter(Boolean)});})()";

    private static final class Entry {
        final String caption;
        final String url;
        final boolean throughChapter;
        Entry(String caption, String url, boolean throughChapter) {
            this.caption = caption;
            this.url = url;
            this.throughChapter = throughChapter;
        }
    }

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
        foreground = Color.parseColor(light ? "#152037" : "#F2F5FF");
        secondary = Color.parseColor(light ? "#526077" : "#AAB5CE");
        textZoom = intent.getIntExtra(ZOOM, 100);
        lawName = intent.getStringExtra(TITLE);
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
        title.setText(lawName);
        title.setTextColor(foreground);
        title.setTextSize(19);
        title.setPadding(dp(8), 0, dp(8), 0);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(button("Оглавление", "Открыть оглавление", () -> {
            pendingArticle = null;
            followed.clear();
            search.setText("");
            reader.loadUrl(contentsUrl);
        }));

        LinearLayout tools = row(layout);
        search = new EditText(this);
        search.setSingleLine(true);
        search.setTextColor(foreground);
        search.setHintTextColor(secondary);
        search.setTextSize(15);
        search.setHint("Номер или название статьи");
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setText(saved == null ? intent.getStringExtra(TERM) : saved.getString(TERM));
        tools.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
        tools.addView(button("↑", "Предыдущее совпадение", () -> { if (reader.isShown()) reader.findNext(false); }));
        tools.addView(button("↓", "Следующее совпадение", () -> { if (reader.isShown()) reader.findNext(true); }));
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
        viewMode = button("Источник", "Переключить вид документа", this::toggleView);
        viewMode.setVisibility(View.GONE);
        source.addView(viewMode);
        source.addView(button("↗", "Открыть страницу в браузере", this::openBrowser));

        articleHeading = new TextView(this);
        articleHeading.setTextColor(foreground);
        articleHeading.setTextSize(20 * textZoom / 100f);
        articleHeading.setPadding(dp(14), dp(8), dp(14), dp(8));
        articleHeading.setVisibility(View.GONE);
        layout.addView(articleHeading);

        list = new ListView(this);
        list.setVisibility(View.GONE);
        adapter = new BaseAdapter() {
            @Override public int getCount() { return filtered.size(); }
            @Override public Entry getItem(int index) { return filtered.get(index); }
            @Override public long getItemId(int index) { return index; }
            @Override public View getView(int index, View recycled, ViewGroup parent) {
                TextView row = recycled instanceof TextView ? (TextView) recycled : new TextView(LawReaderActivity.this);
                row.setTextColor(foreground);
                row.setTextSize(16 * textZoom / 100f);
                row.setPadding(dp(16), dp(12), dp(16), dp(12));
                row.setMinHeight(dp(52));
                row.setGravity(Gravity.CENTER_VERTICAL);
                Entry entry = getItem(index);
                row.setText(ARTICLE.matcher(entry.caption).find() ? articleLabel(entry.caption) : entry.caption);
                return row;
            }
        };
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, position, id) -> {
            Entry entry = filtered.get(position);
            pendingArticle = entry.throughChapter ? articleNumber(entry.caption) : null;
            followed.clear();
            if (entry.throughChapter && documentPath(entry.url).equals(documentPath(reader.getUrl()))) {
                showSource();
                search.setText("Статья " + pendingArticle);
                pendingArticle = null;
                return;
            }
            if (!entry.throughChapter && articleNumber(entry.caption) != null) {
                articleTitles.put(documentPath(entry.url), entry.caption);
            }
            search.setText("");
            reader.loadUrl(entry.url);
        });
        layout.addView(list, new LinearLayout.LayoutParams(-1, 0, 1));

        reader = new WebView(this);
        reader.getSettings().setAllowFileAccess(false);
        reader.getSettings().setAllowContentAccess(false);
        reader.getSettings().setJavaScriptEnabled(true);
        reader.getSettings().setTextZoom(textZoom);
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
            if (!done || !pageReady || !reader.isShown() || search.getText().toString().trim().isEmpty()) return;
            status.setText(count == 0 ? "На этой странице совпадений нет" : "Совпадение " + (index + 1) + " из " + count);
        });
        reader.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isDocument(request.getUrl().toString());
            }

            @Override public void onPageStarted(WebView view, String url, android.graphics.Bitmap icon) {
                loadGeneration++;
                pageReady = false;
                loadFailed = false;
                entries.clear();
                viewMode.setVisibility(View.GONE);
                showSource();
                String caption = articleTitles.get(documentPath(url));
                setArticleHeading(caption);
                status.setText("Загрузка документа…");
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (loadFailed || !isDocument(url)) return;
                pageReady = true;
                findOnPage();
                inspectPage(url);
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

    private String documentPath(String url) { return url == null ? "" : Uri.parse(url).getPath(); }

    private String articleNumber(String title) {
        Matcher matcher = ARTICLE.matcher(title);
        return matcher.find() ? matcher.group(1).replaceAll("[\u2010-\u2015]", "-") : null;
    }

    private String articleLabel(String caption) {
        Matcher matcher = ARTICLE.matcher(caption);
        if (!matcher.find()) return caption;
        String title = matcher.group(2).trim();
        return lawName + " · Статья " + matcher.group(1) + (title.isEmpty() ? "" : "\n" + title);
    }

    private void setArticleHeading(String caption) {
        articleHeading.setVisibility(caption == null ? View.GONE : View.VISIBLE);
        if (caption != null) articleHeading.setText(articleLabel(caption));
    }

    private void inspectPage(String url) {
        int generation = loadGeneration;
        reader.evaluateJavascript(INSPECT_PAGE, result -> {
            if (generation != loadGeneration || !pageReady || isFinishing()) return;
            try {
                Object encoded = new JSONTokener(result).nextValue();
                if (!(encoded instanceof String)) return;
                JSONObject page = new JSONObject((String)encoded);
                String caption = articleTitles.get(documentPath(url));
                JSONArray headings = page.getJSONArray("heading");
                for (int i = 0; caption == null && i < headings.length(); i++) {
                    String heading = headings.getString(i);
                    if (ARTICLE.matcher(heading).find()) caption = heading;
                }
                if (caption != null) {
                    setArticleHeading(caption);
                    return;
                }

                JSONArray links = page.getJSONArray("links");
                java.util.LinkedHashMap<String, Entry> indexed = new java.util.LinkedHashMap<>();
                String scope = documentPath(contentsUrl);
                for (int i = 0; i < links.length(); i++) {
                    JSONArray pair = links.getJSONArray(i);
                    String title = pair.getString(0);
                    String target = pair.getString(1);
                    boolean throughChapter = pair.optBoolean(2, false);
                    if (!isDocument(target) || title.length() > 900) continue;
                    String path = documentPath(target);
                    if (path == null || !path.regionMatches(true, 0, scope, 0, scope.length())) continue;
                    String number = articleNumber(title);
                    if (number == null && (!GROUP.matcher(title).find() || path.equalsIgnoreCase(documentPath(url)))) continue;
                    if (number != null && path.equalsIgnoreCase(documentPath(url))) throughChapter = true;
                    String key = number == null ? path : "article:" + number;
                    Entry previous = indexed.get(key);
                    if (previous == null || previous.throughChapter && !throughChapter) indexed.put(key, new Entry(title, target, throughChapter));
                }
                if (pendingArticle != null) {
                    Entry match = indexed.get("article:" + pendingArticle);
                    followed.add(documentPath(url));
                    if (match != null && !followed.contains(documentPath(match.url)) && followed.size() < 8) {
                        if (!match.throughChapter) {
                            articleTitles.put(documentPath(match.url), match.caption);
                            pendingArticle = null;
                        }
                        reader.loadUrl(match.url);
                        return;
                    }
                    search.setText("Статья " + pendingArticle);
                    pendingArticle = null;
                    return;
                }
                entries.clear();
                entries.addAll(indexed.values());
                if (!entries.isEmpty()) {
                    viewMode.setVisibility(View.VISIBLE);
                    showList();
                }
            } catch (Exception ignored) {
                // The unmodified document remains available if the source changes its markup.
            }
        });
    }

    private void toggleView() { if (list.isShown()) showSource(); else showList(); }

    private void showSource() {
        list.setVisibility(View.GONE);
        reader.setVisibility(View.VISIBLE);
        viewMode.setText("Список");
        findOnPage();
    }

    private void showList() {
        reader.setVisibility(View.GONE);
        list.setVisibility(View.VISIBLE);
        viewMode.setText("Источник");
        findOnPage();
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
        if (list.isShown()) {
            String filter = term.toLowerCase(Locale.ROOT).replace('ё', 'е');
            String exact = articleNumber(term);
            if (exact == null && term.matches("\\d+(?:[.-]\\d+)*")) exact = term;
            filtered.clear();
            for (Entry entry : entries) {
                if (exact != null ? exact.equals(articleNumber(entry.caption))
                        : entry.caption.toLowerCase(Locale.ROOT).replace('ё', 'е').contains(filter)) filtered.add(entry);
            }
            adapter.notifyDataSetChanged();
            status.setText(filtered.isEmpty() ? "Откройте нужную главу или источник" : "Позиций: " + filtered.size());
            return;
        }
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
