package cn.ck.plm.change.support;

import java.util.regex.Pattern;

/**
 * 富文本 → 纯文本（去标签、解实体、保留段落换行）。
 *
 * <h3>为什么需要它</h3>
 * <p>变更请求（ECR）的「变更原因 / 建议方案 / 补充说明」在页面布局里是 {@code rich-text} 控件，
 * 落库是 {@code <p>…</p>} 形式的 HTML；而变更通告（ECN）上这三个字段是 textarea
 * （原因只读带入、方案可改写）。派生时原样复制过去，用户在通告上看到的就是一堆标签，
 * 而且一改一存，标签会被当成正文继续往下传。
 *
 * <p>转换点放在<b>写入侧</b>（派生 / 更新）而不是展示侧：通告的字段语义就是纯文本，
 * 库里存 HTML 迟早会被别的出口（导出、通知、日志）再暴露一次。
 *
 * <h3>为什么自己写而不引 Jsoup</h3>
 * <p>需要的只有"去标签 + 解实体 + 段落变换行"三件事，引一个 HTML 解析器进来还得考虑
 * 白名单与依赖体积；这里的输入是自己产出的受限富文本，不是任意外部 HTML。
 */
public final class HtmlText {

    /** 换行标签：先于块级结束标签处理，否则 &lt;br&gt; 会被当作普通标签直接删掉 */
    private static final Pattern BREAK = Pattern.compile("<br\\s*/?>", Pattern.CASE_INSENSITIVE);

    /** 块级元素的结束标签 → 换行（{@code <p>a</p><p>b</p>} 应还原成两行） */
    private static final Pattern BLOCK_END =
            Pattern.compile("</(p|div|li|h[1-6]|tr|blockquote)>", Pattern.CASE_INSENSITIVE);

    private static final Pattern TAG = Pattern.compile("<[^>]+>");

    /** 行尾空白与连续空行：富文本里常见 {@code <p><br></p>} 占位，转换后不该留下大段空白 */
    private static final Pattern TRAILING_SPACE = Pattern.compile("[ \\t]+\\n");
    private static final Pattern BLANK_LINES = Pattern.compile("\\n{3,}");

    private HtmlText() {
    }

    /**
     * 把富文本转成纯文本。
     *
     * <p>不含 {@code <} 也不含 {@code &} 的输入原样返回 —— 纯文本走一遍正则只会增加
     * 把 {@code "a & b"} 里的 {@code &} 当实体解错的机会。
     *
     * @param html 富文本（或纯文本）；null 返回 null
     * @return 纯文本（已 trim）
     */
    public static String plain(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        if (html.indexOf('<') < 0 && html.indexOf('&') < 0) {
            return html;
        }
        String text = BREAK.matcher(html).replaceAll("\n");
        text = BLOCK_END.matcher(text).replaceAll("\n");
        text = TAG.matcher(text).replaceAll("");
        text = unescape(text);
        text = text.replace('\u00a0', ' ');
        text = TRAILING_SPACE.matcher(text).replaceAll("\n");
        text = BLANK_LINES.matcher(text).replaceAll("\n\n");
        return text.trim();
    }

    /** 实体解码：{@code &amp;} 必须最后处理，否则 {@code &amp;lt;} 会被解成 {@code <} */
    private static String unescape(String text) {
        return text.replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&amp;", "&");
    }
}
