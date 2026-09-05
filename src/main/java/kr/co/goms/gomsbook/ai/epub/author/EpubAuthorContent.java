package kr.co.goms.gomsbook.ai.epub.author;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class EpubAuthorContent {

    private final String title;
    private final List<String> paragraphs;

    public EpubAuthorContent(String title, List<String> paragraphs) {
        this.title = title;
        this.paragraphs = paragraphs == null ? new ArrayList<>() : new ArrayList<>(paragraphs);
    }

    public String getTitle() {
        return title;
    }

    public List<String> getParagraphs() {
        return Collections.unmodifiableList(paragraphs);
    }

    public boolean isEmpty() {
        return paragraphs.isEmpty();
    }

    @Override
    public String toString() {
        return "EpubAuthorContent{title='" + title + "', paragraphs=" + paragraphs + "}";
    }
}