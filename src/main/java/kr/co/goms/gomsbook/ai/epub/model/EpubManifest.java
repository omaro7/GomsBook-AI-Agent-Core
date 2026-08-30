/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * EPUB 패키지 문서의 manifest를 표현합니다.
 *
 * <p>OPF 패키지 문서의 {@code manifest} 요소와 대응하며,
 * EPUB에 포함되거나 참조되는 모든 리소스를 관리합니다.</p>
 *
 * <pre>
 * {@code
 * <manifest>
 *     <item
 *         id="nav"
 *         href="Text/nav.xhtml"
 *         media-type="application/xhtml+xml"
 *         properties="nav"/>
 *
 *     <item
 *         id="chapter01"
 *         href="Text/chapter01.xhtml"
 *         media-type="application/xhtml+xml"/>
 *
 *     <item
 *         id="cover-image"
 *         href="Images/cover.jpg"
 *         media-type="image/jpeg"
 *         properties="cover-image"/>
 * </manifest>
 * }
 * </pre>
 *
 * <p>manifest에서는 다음 조건을 보장합니다.</p>
 *
 * <ul>
 *     <li>리소스 ID는 고유해야 합니다.</li>
 *     <li>리소스 href는 고유해야 합니다.</li>
 *     <li>Navigation Document는 최대 하나만 존재해야 합니다.</li>
 *     <li>EPUB 3 표지 이미지는 최대 하나만 존재해야 합니다.</li>
 *     <li>fallback 및 media-overlay 참조는 실제 리소스를 가리켜야 합니다.</li>
 *     <li>fallback 순환 참조가 없어야 합니다.</li>
 * </ul>
 *
 * <p>이 클래스는 가변 컬렉션이지만 외부에는 수정 불가능한 목록과
 * 맵만 반환합니다.</p>
 */
public final class EpubManifest {

    /**
     * manifest 리소스를 ID 기준으로 관리합니다.
     *
     * <p>{@link LinkedHashMap}을 사용하여 등록 순서를 유지합니다.</p>
     */
    private final Map<String, EpubManifestItem> resourcesById;

    /**
     * manifest 리소스를 정규화된 href 기준으로 관리합니다.
     */
    private final Map<String, EpubManifestItem> resourcesByHref;

    /**
     * 빈 manifest를 생성합니다.
     */
    public EpubManifest() {
        this.resourcesById = new LinkedHashMap<>();
        this.resourcesByHref = new LinkedHashMap<>();
    }

    /**
     * 초기 리소스를 포함하는 manifest를 생성합니다.
     *
     * @param resources 초기 리소스
     */
    public EpubManifest(Collection<EpubManifestItem> resources) {
        this();

        addAll(resources);
    }

    /**
     * builder를 생성합니다.
     *
     * @return manifest builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 빈 manifest를 생성합니다.
     *
     * @return 빈 manifest
     */
    public static EpubManifest empty() {
        return new EpubManifest();
    }

    /**
     * 리소스를 manifest에 추가합니다.
     *
     * @param resource 추가할 리소스
     * @return 현재 manifest
     * @throws IllegalArgumentException ID 또는 href가 중복되는 경우
     */
    public EpubManifest add(EpubManifestItem resource) {
        EpubManifestItem validatedResource = Objects.requireNonNull(
                resource,
                "EPUB resource must not be null."
        );

        validateDuplicateId(validatedResource);
        validateDuplicateHref(validatedResource);
        validateUniqueSpecialProperty(validatedResource);

        resourcesById.put(
                validatedResource.getId(),
                validatedResource
        );

        resourcesByHref.put(
                normalizeHrefKey(validatedResource.getHref()),
                validatedResource
        );

        return this;
    }

    /**
     * 여러 리소스를 manifest에 추가합니다.
     *
     * <p>추가 중 오류가 발생하면 이미 추가된 리소스는 유지됩니다.
     * 전체 원자성이 필요한 경우 {@link Builder}를 사용해 생성하는 것이
     * 적절합니다.</p>
     *
     * @param resources 추가할 리소스
     * @return 현재 manifest
     */
    public EpubManifest addAll(Collection<EpubManifestItem> resources) {
        if (resources == null || resources.isEmpty()) {
            return this;
        }

        for (EpubManifestItem resource : resources) {
            add(resource);
        }

        return this;
    }

    /**
     * 기존 리소스를 추가하거나 교체합니다.
     *
     * <p>동일 ID의 리소스가 있으면 제거한 후 새 리소스를 등록합니다.
     * 다른 ID가 동일 href를 사용하고 있으면 예외가 발생합니다.</p>
     *
     * @param resource 추가하거나 교체할 리소스
     * @return 이전 리소스
     */
    public Optional<EpubManifestItem> put(EpubManifestItem resource) {
        EpubManifestItem validatedResource = Objects.requireNonNull(
                resource,
                "EPUB resource must not be null."
        );

        EpubManifestItem previous = resourcesById.get(
                validatedResource.getId()
        );

        if (previous == null) {
            add(validatedResource);
            return Optional.empty();
        }

        remove(previous.getId());

        try {
            add(validatedResource);
        } catch (RuntimeException exception) {
            add(previous);
            throw exception;
        }

        return Optional.of(previous);
    }

    /**
     * ID에 해당하는 리소스를 제거합니다.
     *
     * @param resourceId 리소스 ID
     * @return 제거된 리소스
     */
    public Optional<EpubManifestItem> remove(String resourceId) {
        String normalizedId = normalizeLookupValue(resourceId);

        if (normalizedId == null) {
            return Optional.empty();
        }

        EpubManifestItem removed = resourcesById.remove(normalizedId);

        if (removed == null) {
            return Optional.empty();
        }

        resourcesByHref.remove(
                normalizeHrefKey(removed.getHref())
        );

        return Optional.of(removed);
    }

    /**
     * href에 해당하는 리소스를 제거합니다.
     *
     * @param href EPUB 내부 경로
     * @return 제거된 리소스
     */
    public Optional<EpubManifestItem> removeByHref(String href) {
        String normalizedHref = normalizeHrefKey(href);

        if (normalizedHref == null) {
            return Optional.empty();
        }

        EpubManifestItem resource = resourcesByHref.get(normalizedHref);

        if (resource == null) {
            return Optional.empty();
        }

        return remove(resource.getId());
    }

    /**
     * ID로 리소스를 조회합니다.
     *
     * @param resourceId 리소스 ID
     * @return 리소스
     */
    public Optional<EpubManifestItem> findById(String resourceId) {
        String normalizedId = normalizeLookupValue(resourceId);

        if (normalizedId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                resourcesById.get(normalizedId)
        );
    }

    /**
     * href로 리소스를 조회합니다.
     *
     * @param href EPUB 내부 경로
     * @return 리소스
     */
    public Optional<EpubManifestItem> findByHref(String href) {
        String normalizedHref = normalizeHrefKey(href);

        if (normalizedHref == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(
                resourcesByHref.get(normalizedHref)
        );
    }

    /**
     * ID에 해당하는 리소스를 반환합니다.
     *
     * @param resourceId 리소스 ID
     * @return 리소스
     * @throws IllegalArgumentException 리소스가 없는 경우
     */
    public EpubManifestItem requireById(String resourceId) {
        return findById(resourceId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "EPUB manifest resource not found by id: "
                                + resourceId
                ));
    }

    /**
     * href에 해당하는 리소스를 반환합니다.
     *
     * @param href EPUB 내부 경로
     * @return 리소스
     * @throws IllegalArgumentException 리소스가 없는 경우
     */
    public EpubManifestItem requireByHref(String href) {
        return findByHref(href)
                .orElseThrow(() -> new IllegalArgumentException(
                        "EPUB manifest resource not found by href: "
                                + href
                ));
    }

    /**
     * ID가 등록되어 있는지 확인합니다.
     *
     * @param resourceId 리소스 ID
     * @return 등록되어 있으면 {@code true}
     */
    public boolean containsId(String resourceId) {
        return findById(resourceId).isPresent();
    }

    /**
     * href가 등록되어 있는지 확인합니다.
     *
     * @param href EPUB 내부 경로
     * @return 등록되어 있으면 {@code true}
     */
    public boolean containsHref(String href) {
        return findByHref(href).isPresent();
    }

    /**
     * 리소스 유형별 목록을 반환합니다.
     *
     * @param resourceType 리소스 유형
     * @return 해당 유형의 리소스 목록
     */
    public List<EpubManifestItem> findByType(
            EpubResourceType resourceType
    ) {
        if (resourceType == null) {
            return Collections.emptyList();
        }

        return filter(resource ->
                resource.getResourceType() == resourceType
        );
    }

    /**
     * 리소스 분류별 목록을 반환합니다.
     *
     * @param category 리소스 분류
     * @return 해당 분류의 리소스 목록
     */
    public List<EpubManifestItem> findByCategory(
            EpubResourceType.Category category
    ) {
        if (category == null) {
            return Collections.emptyList();
        }

        return filter(resource ->
                resource.getResourceType().getCategory() == category
        );
    }

    /**
     * 지정한 manifest property를 가진 리소스를 반환합니다.
     *
     * @param property manifest property
     * @return 해당 속성을 가진 리소스 목록
     */
    public List<EpubManifestItem> findByProperty(String property) {
        if (property == null || property.isBlank()) {
            return Collections.emptyList();
        }

        return filter(resource -> resource.hasProperty(property));
    }

    /**
     * 조건에 맞는 리소스를 반환합니다.
     *
     * @param predicate 검색 조건
     * @return 조건에 맞는 리소스 목록
     */
    public List<EpubManifestItem> filter(
            Predicate<EpubManifestItem> predicate
    ) {
        Objects.requireNonNull(
                predicate,
                "EPUB resource predicate must not be null."
        );

        return resourcesById.values()
                .stream()
                .filter(predicate)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * EPUB Navigation Document를 반환합니다.
     *
     * @return nav 속성을 가진 XHTML 리소스
     */
    public Optional<EpubManifestItem> getNavigationDocument() {
        return resourcesById.values()
                .stream()
                .filter(EpubManifestItem::isNavigationDocument)
                .findFirst();
    }

    /**
     * EPUB 3 표지 이미지를 반환합니다.
     *
     * @return cover-image 속성을 가진 이미지 리소스
     */
    public Optional<EpubManifestItem> getCoverImage() {
        return resourcesById.values()
                .stream()
                .filter(EpubManifestItem::isCoverImage)
                .findFirst();
    }

    /**
     * NCX 리소스를 반환합니다.
     *
     * @return NCX 리소스
     */
    public Optional<EpubManifestItem> getNcxResource() {
        return resourcesById.values()
                .stream()
                .filter(resource ->
                        resource.getResourceType()
                                == EpubResourceType.NCX
                )
                .findFirst();
    }

    /**
     * XHTML 콘텐츠 문서 목록을 반환합니다.
     *
     * @return XHTML 리소스 목록
     */
    public List<EpubManifestItem> getXhtmlResources() {
        return findByType(EpubResourceType.XHTML);
    }

    /**
     * 이미지 리소스 목록을 반환합니다.
     *
     * @return 이미지 리소스 목록
     */
    public List<EpubManifestItem> getImageResources() {
        return filter(resource ->
                resource.getResourceType().isImage()
        );
    }

    /**
     * 스타일시트 리소스 목록을 반환합니다.
     *
     * @return CSS 리소스 목록
     */
    public List<EpubManifestItem> getStylesheetResources() {
        return findByType(EpubResourceType.CSS);
    }

    /**
     * 글꼴 리소스 목록을 반환합니다.
     *
     * @return 글꼴 리소스 목록
     */
    public List<EpubManifestItem> getFontResources() {
        return filter(resource ->
                resource.getResourceType().isFont()
        );
    }

    /**
     * 오디오 리소스 목록을 반환합니다.
     *
     * @return 오디오 리소스 목록
     */
    public List<EpubManifestItem> getAudioResources() {
        return filter(resource ->
                resource.getResourceType().isAudio()
        );
    }

    /**
     * 비디오 리소스 목록을 반환합니다.
     *
     * @return 비디오 리소스 목록
     */
    public List<EpubManifestItem> getVideoResources() {
        return filter(resource ->
                resource.getResourceType().isVideo()
        );
    }

    /**
     * Media Overlay SMIL 목록을 반환합니다.
     *
     * @return SMIL 리소스 목록
     */
    public List<EpubManifestItem> getMediaOverlayResources() {
        return findByType(EpubResourceType.SMIL);
    }

    /**
     * 원격 리소스 목록을 반환합니다.
     *
     * @return 원격 리소스 목록
     */
    public List<EpubManifestItem> getRemoteResources() {
        return filter(EpubManifestItem::isRemote);
    }

    /**
     * EPUB 파일에 실제 포함되는 리소스 목록을 반환합니다.
     *
     * @return 포함 리소스 목록
     */
    public List<EpubManifestItem> getIncludedResources() {
        return filter(EpubManifestItem::isIncluded);
    }

    /**
     * EPUB 파일에 포함하지 않는 외부 리소스 목록을 반환합니다.
     *
     * @return 외부 또는 제외 리소스 목록
     */
    public List<EpubManifestItem> getExcludedResources() {
        return filter(resource -> !resource.isIncluded());
    }

    /**
     * 등록 순서대로 모든 리소스를 반환합니다.
     *
     * @return 수정할 수 없는 리소스 목록
     */
    public List<EpubManifestItem> getResources() {
        return Collections.unmodifiableList(
                new ArrayList<>(resourcesById.values())
        );
    }

    /**
     * ID 기준 리소스 맵을 반환합니다.
     *
     * @return 수정할 수 없는 리소스 맵
     */
    public Map<String, EpubManifestItem> getResourcesById() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(resourcesById)
        );
    }

    /**
     * 등록된 리소스 ID 집합을 반환합니다.
     *
     * @return 리소스 ID 집합
     */
    public Set<String> getResourceIds() {
        return Collections.unmodifiableSet(
                resourcesById.keySet()
        );
    }

    /**
     * 리소스 수를 반환합니다.
     *
     * @return 리소스 수
     */
    public int size() {
        return resourcesById.size();
    }

    /**
     * manifest가 비어 있는지 확인합니다.
     *
     * @return 비어 있으면 {@code true}
     */
    public boolean isEmpty() {
        return resourcesById.isEmpty();
    }

    /**
     * 모든 리소스를 제거합니다.
     */
    public void clear() {
        resourcesById.clear();
        resourcesByHref.clear();
    }

    /**
     * manifest 전체 참조 관계를 검증합니다.
     *
     * @throws IllegalStateException 참조 오류 또는 순환 참조가 있는 경우
     */
    public void validate() {
        validateNavigationDocument();
        validateCoverImage();
        validateFallbackReferences();
        validateMediaOverlayReferences();
        validateFallbackCycles();
    }

    /**
     * 지정한 EPUB 버전을 기준으로 manifest를 검증합니다.
     *
     * @param version EPUB 버전
     * @throws IllegalStateException 버전과 호환되지 않는 경우
     */
    public void validate(EpubVersion version) {
        if (version == null) {
            throw new IllegalArgumentException(
                    "EPUB version must not be null."
            );
        }

        validate();

        for (EpubManifestItem resource : resourcesById.values()) {
            if (!resource.isSupportedBy(version)) {
                throw new IllegalStateException(
                        "EPUB resource type is not supported by version "
                                + version
                                + ": "
                                + resource.getId()
                                + " ("
                                + resource.getResourceType()
                                + ")"
                );
            }
        }

        if (version.isEpub3()
                && getNavigationDocument().isEmpty()) {
            throw new IllegalStateException(
                    "EPUB 3 manifest requires one Navigation Document."
            );
        }

        if (version.isEpub2()
                && getNcxResource().isEmpty()) {
            throw new IllegalStateException(
                    "EPUB 2 manifest requires an NCX resource."
            );
        }
    }

    /**
     * fallback 체인의 최종 리소스를 반환합니다.
     *
     * @param resourceId 시작 리소스 ID
     * @return fallback 체인의 마지막 리소스
     */
    public EpubManifestItem resolveFallback(String resourceId) {
        EpubManifestItem current = requireById(resourceId);
        Set<String> visited = new java.util.LinkedHashSet<>();

        while (current.hasFallback()) {
            if (!visited.add(current.getId())) {
                throw new IllegalStateException(
                        "Circular EPUB fallback reference detected: "
                                + String.join(" -> ", visited)
                                + " -> "
                                + current.getId()
                );
            }

            String fallbackId = current.getFallbackId()
                    .orElseThrow();

            current = requireById(fallbackId);
        }

        return current;
    }

    /**
     * 원본 리소스에 연결된 Media Overlay를 반환합니다.
     *
     * @param resourceId 콘텐츠 리소스 ID
     * @return SMIL 리소스
     */
    public Optional<EpubManifestItem> resolveMediaOverlay(
            String resourceId
    ) {
        EpubManifestItem resource = requireById(resourceId);

        return resource.getMediaOverlayId()
                .flatMap(this::findById);
    }

    /**
     * 현재 manifest의 독립 복사본을 생성합니다.
     *
     * <p>{@link EpubManifestItem}가 불변 객체이므로 리소스 인스턴스는
     * 공유합니다.</p>
     *
     * @return 복사된 manifest
     */
    public EpubManifest copy() {
        return new EpubManifest(getResources());
    }

    private void validateDuplicateId(EpubManifestItem resource) {
        if (resourcesById.containsKey(resource.getId())) {
            throw new IllegalArgumentException(
                    "Duplicate EPUB manifest resource id: "
                            + resource.getId()
            );
        }
    }

    private void validateDuplicateHref(EpubManifestItem resource) {
        String normalizedHref = normalizeHrefKey(
                resource.getHref()
        );

        EpubManifestItem existing = resourcesByHref.get(normalizedHref);

        if (existing != null) {
            throw new IllegalArgumentException(
                    "Duplicate EPUB manifest resource href: "
                            + resource.getHref()
                            + " (existing id: "
                            + existing.getId()
                            + ")"
            );
        }
    }

    private void validateUniqueSpecialProperty(
            EpubManifestItem resource
    ) {
        if (resource.isNavigationDocument()
                && getNavigationDocument().isPresent()) {
            throw new IllegalArgumentException(
                    "EPUB manifest can contain only one "
                            + "Navigation Document."
            );
        }

        if (resource.isCoverImage()
                && getCoverImage().isPresent()) {
            throw new IllegalArgumentException(
                    "EPUB manifest can contain only one cover image."
            );
        }
    }

    private void validateNavigationDocument() {
        List<EpubManifestItem> navigationDocuments =
                resourcesById.values()
                        .stream()
                        .filter(EpubManifestItem::isNavigationDocument)
                        .toList();

        if (navigationDocuments.size() > 1) {
            throw new IllegalStateException(
                    "EPUB manifest contains multiple "
                            + "Navigation Documents: "
                            + navigationDocuments.stream()
                                    .map(EpubManifestItem::getId)
                                    .collect(Collectors.joining(", "))
            );
        }
    }

    private void validateCoverImage() {
        List<EpubManifestItem> coverImages =
                resourcesById.values()
                        .stream()
                        .filter(EpubManifestItem::isCoverImage)
                        .toList();

        if (coverImages.size() > 1) {
            throw new IllegalStateException(
                    "EPUB manifest contains multiple cover images: "
                            + coverImages.stream()
                                    .map(EpubManifestItem::getId)
                                    .collect(Collectors.joining(", "))
            );
        }
    }

    private void validateFallbackReferences() {
        for (EpubManifestItem resource : resourcesById.values()) {
            resource.getFallbackId().ifPresent(fallbackId -> {
                EpubManifestItem fallback = resourcesById.get(fallbackId);

                if (fallback == null) {
                    throw new IllegalStateException(
                            "EPUB fallback resource not found: "
                                    + resource.getId()
                                    + " -> "
                                    + fallbackId
                    );
                }

                if (fallback.getId().equals(resource.getId())) {
                    throw new IllegalStateException(
                            "EPUB resource cannot reference itself "
                                    + "as fallback: "
                                    + resource.getId()
                    );
                }
            });
        }
    }

    private void validateMediaOverlayReferences() {
        for (EpubManifestItem resource : resourcesById.values()) {
            resource.getMediaOverlayId().ifPresent(mediaOverlayId -> {
                EpubManifestItem mediaOverlay =
                        resourcesById.get(mediaOverlayId);

                if (mediaOverlay == null) {
                    throw new IllegalStateException(
                            "EPUB media overlay resource not found: "
                                    + resource.getId()
                                    + " -> "
                                    + mediaOverlayId
                    );
                }

                if (mediaOverlay.getResourceType()
                        != EpubResourceType.SMIL) {
                    throw new IllegalStateException(
                            "EPUB media-overlay reference must point "
                                    + "to a SMIL resource: "
                                    + resource.getId()
                                    + " -> "
                                    + mediaOverlayId
                                    + " ("
                                    + mediaOverlay.getResourceType()
                                    + ")"
                    );
                }
            });
        }
    }

    private void validateFallbackCycles() {
        for (EpubManifestItem resource : resourcesById.values()) {
            detectFallbackCycle(resource);
        }
    }

    private void detectFallbackCycle(EpubManifestItem start) {
        Set<String> visited = new java.util.LinkedHashSet<>();
        EpubManifestItem current = start;

        while (current.hasFallback()) {
            if (!visited.add(current.getId())) {
                throw new IllegalStateException(
                        "Circular EPUB fallback reference detected: "
                                + String.join(" -> ", visited)
                                + " -> "
                                + current.getId()
                );
            }

            String fallbackId = current.getFallbackId()
                    .orElseThrow();

            EpubManifestItem fallback = resourcesById.get(fallbackId);

            if (fallback == null) {
                return;
            }

            current = fallback;
        }
    }

    private static String normalizeLookupValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static String normalizeHrefKey(String href) {
        if (href == null || href.isBlank()) {
            return null;
        }

        String normalized = href.trim()
                .replace('\\', '/');

        while (normalized.startsWith("./")) {
            normalized = normalized.substring(2);
        }

        return normalized;
    }

    @Override
    public String toString() {
        return "EpubManifest{"
                + "resourceCount=" + resourcesById.size()
                + ", navigationDocument="
                + getNavigationDocument()
                        .map(EpubManifestItem::getId)
                        .orElse(null)
                + ", coverImage="
                + getCoverImage()
                        .map(EpubManifestItem::getId)
                        .orElse(null)
                + '}';
    }

    /**
     * {@link EpubManifest} 생성 빌더입니다.
     *
     * <p>builder는 임시 manifest에서 모든 중복 검사를 수행하며,
     * {@link #build()} 호출 시 전체 참조 관계도 검증합니다.</p>
     */
    public static final class Builder {

        private final List<EpubManifestItem> resources =
                new ArrayList<>();

        private boolean validateOnBuild = true;

        private EpubVersion version;

        private Builder() {
        }

        public Builder resource(EpubManifestItem resource) {
            resources.add(
                    Objects.requireNonNull(
                            resource,
                            "EPUB resource must not be null."
                    )
            );
            return this;
        }

        public Builder resources(
                Collection<EpubManifestItem> resources
        ) {
            if (resources == null) {
                return this;
            }

            for (EpubManifestItem resource : resources) {
                resource(resource);
            }

            return this;
        }

        public Builder xhtml(
                String id,
                String href,
                String sourcePath
        ) {
            return resource(
                    EpubManifestItem.builder(id, href)
                            .resourceType(EpubResourceType.XHTML)
                            .sourcePath(sourcePath)
                            .build()
            );
        }

        public Builder navigationDocument(
                String id,
                String href,
                String sourcePath
        ) {
            return resource(
                    EpubManifestItem.builder(id, href)
                            .resourceType(EpubResourceType.XHTML)
                            .navigationDocument(true)
                            .sourcePath(sourcePath)
                            .build()
            );
        }

        public Builder coverImage(
                String id,
                String href,
                String sourcePath
        ) {
            EpubResourceType resourceType =
                    EpubResourceType.fromFileName(href)
                            .orElse(EpubResourceType.UNKNOWN);

            return resource(
                    EpubManifestItem.builder(id, href)
                            .resourceType(resourceType)
                            .coverImage(true)
                            .sourcePath(sourcePath)
                            .build()
            );
        }

        public Builder validateOnBuild(boolean validateOnBuild) {
            this.validateOnBuild = validateOnBuild;
            return this;
        }

        /**
         * 빌드 시 특정 EPUB 버전 기준 검증을 활성화합니다.
         *
         * @param version EPUB 버전
         * @return 현재 builder
         */
        public Builder version(EpubVersion version) {
            this.version = version;
            return this;
        }

        public EpubManifest build() {
            EpubManifest manifest =
                    new EpubManifest(resources);

            if (validateOnBuild) {
                if (version == null) {
                    manifest.validate();
                } else {
                    manifest.validate(version);
                }
            }

            return manifest;
        }
    }
}