package kr.co.goms.gomsbook.ai.epub.author;

public class EpubAuthorPageState {

    private final boolean fileExists;
    private final boolean manifestRegistered;
    private final boolean spineRegistered;
    private final boolean navigationRegistered;

    public EpubAuthorPageState(boolean fileExists, boolean manifestRegistered, boolean spineRegistered, boolean navigationRegistered) {
        this.fileExists = fileExists;
        this.manifestRegistered = manifestRegistered;
        this.spineRegistered = spineRegistered;
        this.navigationRegistered = navigationRegistered;
    }

    public boolean isFileExists() {
        return fileExists;
    }

    public boolean isManifestRegistered() {
        return manifestRegistered;
    }

    public boolean isSpineRegistered() {
        return spineRegistered;
    }

    public boolean isNavigationRegistered() {
        return navigationRegistered;
    }

    public boolean isEmpty() {
        return !fileExists && !manifestRegistered && !spineRegistered && !navigationRegistered;
    }
    
    public boolean isValid() {
        return fileExists && manifestRegistered && spineRegistered;
    }

    public boolean isFullyRegistered() {
        return fileExists && manifestRegistered && spineRegistered && navigationRegistered;
    }

    @Override
    public String toString() {
        return "EpubAuthorPageState{fileExists=" + fileExists + ", manifestRegistered=" + manifestRegistered + ", spineRegistered=" + spineRegistered + ", navigationRegistered=" + navigationRegistered + "}";
    }
    
}