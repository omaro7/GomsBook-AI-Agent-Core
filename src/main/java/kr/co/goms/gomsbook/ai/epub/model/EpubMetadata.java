/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.model;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * EPUB 패키지 문서의 metadata 요소를 표현합니다.
 *
 * <p>OPF 패키지 문서의 {@code metadata} 요소 내부에 포함되는
 * Dublin Core 메타데이터와 EPUB 3 property 메타데이터를 관리합니다.</p>
 *
 * <pre>
 * {@code
 * <metadata
 *     xmlns:dc="http://purl.org/dc/elements/1.1/"
 *     xmlns:dcterms="http://purl.org/dc/terms/">
 *
 *     <dc:identifier id="book-id">
 *         urn:isbn:9780000000000
 *     </dc:identifier>
 *
 *     <dc:title id="title-main">
 *         점심시간, 서울을 걷다
 *     </dc:title>
 *
 *     <dc:creator id="creator-author">
 *         한정훈
 *     </dc:creator>
 *
 *     <dc:language>ko</dc:language>
 *
 *     <meta property="dcterms:modified">
 *         2026-08-06T23:51:00Z
 *     </meta>
 * </metadata>
 * }
 * </pre>
 *
 * <p>다음 무결성 규칙을 관리합니다.</p>
 *
 * <ul>
 *     <li>메타데이터 ID 중복 금지</li>
 *     <li>{@code refines} 대상 ID 존재 여부 확인</li>
 *     <li>EPUB 필수 메타데이터 확인</li>
 *     <li>고유 식별자 ID 및 값 관리</li>
 *     <li>EPUB 3 수정일 메타데이터 관리</li>
 *     <li>기본 제목과 기본 언어 관리</li>
 * </ul>
 */
public final class EpubMetadata {

    /**
     * 등록 순서를 유지하는 메타데이터 목록입니다.
     */
    private final List<EpubMetadataItem> entries;

    /**
     * ID가 지정된 메타데이터를 ID 기준으로 관리합니다.
     */
    private final Map<String, EpubMetadataItem> entriesById;

    /**
     * OPF package 요소의 unique-identifier 속성이 참조할
     * dc:identifier 요소의 ID입니다.
     */
    private String uniqueIdentifierId;

    /**
     * 빈 메타데이터를 생성합니다.
     */
    public EpubMetadata() {
        this.entries = new ArrayList<>();
        this.entriesById = new LinkedHashMap<>();
    }

    /**
     * 초기 메타데이터 항목을 포함하여 생성합니다.
     *
     * @param entries 초기 메타데이터 항목
     */
    public EpubMetadata(Collection<EpubMetadataItem> entries) {
        this();

        addAll(entries);
    }

    /**
     * Builder를 생성합니다.
     *
     * @return 메타데이터 Builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * 빈 메타데이터를 생성합니다.
     *
     * @return 빈 메타데이터
     */
    public static EpubMetadata empty() {
        return new EpubMetadata();
    }

    /**
     * 메타데이터 항목을 추가합니다.
     *
     * @param entry 추가할 메타데이터
     * @return 현재 메타데이터
     */
    public EpubMetadata add(EpubMetadataItem entry) {
        EpubMetadataItem validatedEntry = Objects.requireNonNull(
                entry,
                "EPUB metadata entry must not be null."
        );

        validateDuplicateId(validatedEntry);

        entries.add(validatedEntry);

        validatedEntry.getId().ifPresent(id ->
                entriesById.put(id, validatedEntry)
        );

        return this;
    }

    /**
     * 여러 메타데이터 항목을 순서대로 추가합니다.
     *
     * @param metadataEntries 추가할 메타데이터
     * @return 현재 메타데이터
     */
    public EpubMetadata addAll(
            Collection<EpubMetadataItem> metadataEntries
    ) {
        if (metadataEntries == null || metadataEntries.isEmpty()) {
            return this;
        }

        for (EpubMetadataItem entry : metadataEntries) {
            add(entry);
        }

        return this;
    }

    /**
     * 메타데이터 항목을 추가하거나 교체합니다.
     *
     * <p>ID가 지정된 항목은 동일 ID의 기존 항목을 교체합니다.
     * ID가 없는 항목은 항상 새 항목으로 추가합니다.</p>
     *
     * @param entry 추가하거나 교체할 항목
     * @return 교체된 기존 항목
     */
    public Optional<EpubMetadataItem> put(
            EpubMetadataItem entry
    ) {
        EpubMetadataItem validatedEntry = Objects.requireNonNull(
                entry,
                "EPUB metadata entry must not be null."
        );

        Optional<String> entryId = validatedEntry.getId();

        if (entryId.isEmpty()) {
            add(validatedEntry);
            return Optional.empty();
        }

        EpubMetadataItem previous = entriesById.get(entryId.get());

        if (previous == null) {
            add(validatedEntry);
            return Optional.empty();
        }

        int index = entries.indexOf(previous);

        entries.set(index, validatedEntry);
        entriesById.put(entryId.get(), validatedEntry);

        return Optional.of(previous);
    }

    /**
     * ID로 메타데이터 항목을 제거합니다.
     *
     * <p>제거되는 항목을 다른 메타데이터가 {@code refines}로
     * 참조하는 경우 함께 제거하지 않으며, 이후 {@link #validate()}에서
     * 참조 오류가 검출됩니다.</p>
     *
     * @param id 메타데이터 ID
     * @return 제거된 항목
     */
    public Optional<EpubMetadataItem> removeById(String id) {
        String normalizedId = normalizeLookupValue(id);

        if (normalizedId == null) {
            return Optional.empty();
        }

        EpubMetadataItem removed = entriesById.remove(normalizedId);

        if (removed == null) {
            return Optional.empty();
        }

        entries.remove(removed);

        if (normalizedId.equals(uniqueIdentifierId)) {
            uniqueIdentifierId = null;
        }

        return Optional.of(removed);
    }

    /**
     * 지정한 항목을 제거합니다.
     *
     * @param entry 제거할 항목
     * @return 제거되었으면 {@code true}
     */
    public boolean remove(EpubMetadataItem entry) {
        if (entry == null) {
            return false;
        }

        boolean removed = entries.remove(entry);

        if (!removed) {
            return false;
        }

        entry.getId().ifPresent(id -> {
            entriesById.remove(id);

            if (id.equals(uniqueIdentifierId)) {
                uniqueIdentifierId = null;
            }
        });

        return true;
    }

    /**
     * ID로 메타데이터 항목을 조회합니다.
     *
     * @param id 메타데이터 ID
     * @return 메타데이터 항목
     */
    public Optional<EpubMetadataItem> findById(String id) {
        String normalizedId = normalizeLookupValue(id);

        if (normalizedId == null) {
            return Optional.empty();
        }

        return Optional.ofNullable(entriesById.get(normalizedId));
    }

    /**
     * ID에 해당하는 메타데이터 항목을 반환합니다.
     *
     * @param id 메타데이터 ID
     * @return 메타데이터 항목
     */
    public EpubMetadataItem requireById(String id) {
        return findById(id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "EPUB metadata entry not found by id: " + id
                ));
    }

    /**
     * Dublin Core 요소 이름으로 메타데이터를 조회합니다.
     *
     * @param elementName Dublin Core 요소 이름
     * @return 일치하는 항목 목록
     */
    public List<EpubMetadataItem> findByElement(
            String elementName
    ) {
        if (elementName == null || elementName.isBlank()) {
            return Collections.emptyList();
        }

        return filter(entry ->
                entry.isDublinCore()
                        && entry.isElement(elementName)
        );
    }

    /**
     * EPUB property 이름으로 메타데이터를 조회합니다.
     *
     * @param property property 이름
     * @return 일치하는 항목 목록
     */
    public List<EpubMetadataItem> findByProperty(
            String property
    ) {
        if (property == null || property.isBlank()) {
            return Collections.emptyList();
        }

        return filter(entry ->
                entry.isMetaProperty()
                        && entry.hasProperty(property)
        );
    }

    /**
     * 지정한 메타데이터 ID를 정제하는 항목을 반환합니다.
     *
     * @param targetId 정제 대상 메타데이터 ID
     * @return refinement 항목 목록
     */
    public List<EpubMetadataItem> findRefinements(
            String targetId
    ) {
        String normalizedId = normalizeLookupValue(targetId);

        if (normalizedId == null) {
            return Collections.emptyList();
        }

        return filter(entry ->
                entry.getRefinedTargetId()
                        .map(normalizedId::equals)
                        .orElse(false)
        );
    }

    /**
     * 특정 대상과 property를 모두 만족하는 refinement를 반환합니다.
     *
     * @param targetId 대상 메타데이터 ID
     * @param property refinement property
     * @return 일치하는 refinement 목록
     */
    public List<EpubMetadataItem> findRefinements(
            String targetId,
            String property
    ) {
        if (property == null || property.isBlank()) {
            return Collections.emptyList();
        }

        return findRefinements(targetId)
                .stream()
                .filter(entry -> entry.hasProperty(property))
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * 조건에 일치하는 메타데이터를 반환합니다.
     *
     * @param predicate 검색 조건
     * @return 조건에 맞는 메타데이터 목록
     */
    public List<EpubMetadataItem> filter(
            Predicate<EpubMetadataItem> predicate
    ) {
        Objects.requireNonNull(
                predicate,
                "EPUB metadata predicate must not be null."
        );

        return entries.stream()
                .filter(predicate)
                .collect(Collectors.toUnmodifiableList());
    }

    /**
     * 모든 메타데이터를 등록 순서대로 반환합니다.
     *
     * @return 수정할 수 없는 메타데이터 목록
     */
    public List<EpubMetadataItem> getEntries() {
        return Collections.unmodifiableList(
                new ArrayList<>(entries)
        );
    }

    /**
     * ID 기준 메타데이터 맵을 반환합니다.
     *
     * @return 수정할 수 없는 메타데이터 맵
     */
    public Map<String, EpubMetadataItem> getEntriesById() {
        return Collections.unmodifiableMap(
                new LinkedHashMap<>(entriesById)
        );
    }

    /**
     * 등록된 메타데이터 ID를 반환합니다.
     *
     * @return 메타데이터 ID 집합
     */
    public Set<String> getEntryIds() {
        return Collections.unmodifiableSet(entriesById.keySet());
    }

    /**
     * 메타데이터 개수를 반환합니다.
     *
     * @return 항목 수
     */
    public int size() {
        return entries.size();
    }

    /**
     * 메타데이터가 비어 있는지 확인합니다.
     *
     * @return 비어 있으면 {@code true}
     */
    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * 모든 메타데이터를 제거합니다.
     */
    public void clear() {
        entries.clear();
        entriesById.clear();
        uniqueIdentifierId = null;
    }

    /**
     * 제목 메타데이터를 반환합니다.
     *
     * @return dc:title 목록
     */
    public List<EpubMetadataItem> getTitles() {
        return filter(EpubMetadataItem::isTitle);
    }

    /**
     * 첫 번째 제목을 반환합니다.
     *
     * @return 기본 제목
     */
    public Optional<EpubMetadataItem> getPrimaryTitle() {
        /*
         * title-type=main refinement가 지정된 제목을 우선합니다.
         */
        for (EpubMetadataItem title : getTitles()) {
            Optional<String> titleId = title.getId();

            if (titleId.isPresent()
                    && hasRefinementValue(
                            titleId.get(),
                            "title-type",
                            "main"
                    )) {
                return Optional.of(title);
            }
        }

        return getTitles().stream().findFirst();
    }

    /**
     * 기본 제목 문자열을 반환합니다.
     *
     * @return 기본 제목 값
     */
    public Optional<String> getPrimaryTitleValue() {
        return getPrimaryTitle()
                .map(EpubMetadataItem::getValue);
    }

    /**
     * 저자 및 기여자 메타데이터를 반환합니다.
     *
     * @return dc:creator 목록
     */
    public List<EpubMetadataItem> getCreators() {
        return filter(EpubMetadataItem::isCreator);
    }

    /**
     * 첫 번째 저자를 반환합니다.
     *
     * @return 기본 저자
     */
    public Optional<EpubMetadataItem> getPrimaryCreator() {
        /*
         * role=aut refinement가 있는 creator를 우선합니다.
         */
        for (EpubMetadataItem creator : getCreators()) {
            Optional<String> creatorId = creator.getId();

            if (creatorId.isPresent()
                    && hasRefinementValue(
                            creatorId.get(),
                            "role",
                            "aut"
                    )) {
                return Optional.of(creator);
            }
        }

        return getCreators().stream().findFirst();
    }

    /**
     * 기본 저자명을 반환합니다.
     *
     * @return 기본 저자명
     */
    public Optional<String> getPrimaryCreatorValue() {
        return getPrimaryCreator()
                .map(EpubMetadataItem::getValue);
    }

    /**
     * 식별자 메타데이터 목록을 반환합니다.
     *
     * @return dc:identifier 목록
     */
    public List<EpubMetadataItem> getIdentifiers() {
        return filter(EpubMetadataItem::isIdentifier);
    }

    /**
     * 언어 메타데이터 목록을 반환합니다.
     *
     * @return dc:language 목록
     */
    public List<EpubMetadataItem> getLanguages() {
        return filter(EpubMetadataItem::isLanguage);
    }

    /**
     * 첫 번째 언어 값을 반환합니다.
     *
     * @return 기본 언어
     */
    public Optional<String> getPrimaryLanguage() {
        return getLanguages()
                .stream()
                .findFirst()
                .map(EpubMetadataItem::getValue);
    }

    /**
     * 출판사 메타데이터 목록을 반환합니다.
     *
     * @return dc:publisher 목록
     */
    public List<EpubMetadataItem> getPublishers() {
        return filter(EpubMetadataItem::isPublisher);
    }

    /**
     * 주제 메타데이터 목록을 반환합니다.
     *
     * @return dc:subject 목록
     */
    public List<EpubMetadataItem> getSubjects() {
        return findByElement("dc:subject");
    }

    /**
     * 설명 메타데이터 목록을 반환합니다.
     *
     * @return dc:description 목록
     */
    public List<EpubMetadataItem> getDescriptions() {
        return findByElement("dc:description");
    }

    /**
     * 권리 메타데이터 목록을 반환합니다.
     *
     * @return dc:rights 목록
     */
    public List<EpubMetadataItem> getRightsEntries() {
        return findByElement("dc:rights");
    }

    /**
     * 출판일 메타데이터 목록을 반환합니다.
     *
     * @return dc:date 목록
     */
    public List<EpubMetadataItem> getDates() {
        return findByElement("dc:date");
    }

    /**
     * EPUB 3 수정일 메타데이터를 반환합니다.
     *
     * @return dcterms:modified 항목
     */
    public Optional<EpubMetadataItem> getModifiedEntry() {
        return entries.stream()
                .filter(EpubMetadataItem::isModifiedDate)
                .findFirst();
    }

    /**
     * EPUB 3 수정일 값을 반환합니다.
     *
     * @return 수정일 문자열
     */
    public Optional<String> getModifiedValue() {
        return getModifiedEntry()
                .map(EpubMetadataItem::getValue);
    }

    /**
     * OPF package의 unique-identifier가 참조할 ID를 반환합니다.
     *
     * @return 고유 식별자 ID
     */
    public Optional<String> getUniqueIdentifierId() {
        return Optional.ofNullable(uniqueIdentifierId);
    }

    /**
     * 고유 식별자 ID를 설정합니다.
     *
     * <p>해당 ID의 메타데이터는 반드시 {@code dc:identifier}여야
     * 합니다. 실제 존재 여부는 설정 시 즉시 확인합니다.</p>
     *
     * @param uniqueIdentifierId 고유 식별자 메타데이터 ID
     */
    public void setUniqueIdentifierId(String uniqueIdentifierId) {
        String normalized = normalizeOptionalIdentifier(
                uniqueIdentifierId
        );

        if (normalized == null) {
            this.uniqueIdentifierId = null;
            return;
        }

        EpubMetadataItem identifier = findById(normalized)
                .orElseThrow(() -> new IllegalArgumentException(
                        "EPUB unique identifier metadata not found: "
                                + normalized
                ));

        if (!identifier.isIdentifier()) {
            throw new IllegalArgumentException(
                    "EPUB unique identifier must reference "
                            + "a dc:identifier entry: "
                            + normalized
            );
        }

        this.uniqueIdentifierId = normalized;
    }

    /**
     * OPF package의 고유 식별자 메타데이터를 반환합니다.
     *
     * @return 고유 식별자 항목
     */
    public Optional<EpubMetadataItem> getUniqueIdentifier() {
        if (uniqueIdentifierId == null) {
            return Optional.empty();
        }

        return findById(uniqueIdentifierId);
    }

    /**
     * 고유 식별자의 실제 값을 반환합니다.
     *
     * @return ISBN, UUID 또는 기타 식별자
     */
    public Optional<String> getUniqueIdentifierValue() {
        return getUniqueIdentifier()
                .map(EpubMetadataItem::getValue);
    }

    /**
     * 고유 식별자 항목을 추가하고 package 참조 ID로 설정합니다.
     *
     * @param id    식별자 요소 ID
     * @param value 식별자 값
     * @return 현재 메타데이터
     */
    public EpubMetadata addUniqueIdentifier(
            String id,
            String value
    ) {
        EpubMetadataItem identifier =
                EpubMetadataItem.identifier(id, value);

        add(identifier);
        setUniqueIdentifierId(id);

        return this;
    }

    /**
     * 제목을 추가합니다.
     *
     * @param value 제목
     * @return 현재 메타데이터
     */
    public EpubMetadata addTitle(String value) {
        return add(EpubMetadataItem.title(value));
    }

    /**
     * ID가 있는 제목을 추가합니다.
     *
     * @param id    메타데이터 ID
     * @param value 제목
     * @return 현재 메타데이터
     */
    public EpubMetadata addTitle(
            String id,
            String value
    ) {
        return add(EpubMetadataItem.title(id, value));
    }

    /**
     * 기본 제목과 title-type refinement를 추가합니다.
     *
     * @param id    제목 ID
     * @param value 제목
     * @return 현재 메타데이터
     */
    public EpubMetadata addMainTitle(
            String id,
            String value
    ) {
        add(EpubMetadataItem.title(id, value));

        add(
                EpubMetadataItem.meta("title-type", "main")
                        .refines(id)
                        .build()
        );

        return this;
    }

    /**
     * 저자를 추가합니다.
     *
     * @param value 저자명
     * @return 현재 메타데이터
     */
    public EpubMetadata addCreator(String value) {
        return add(EpubMetadataItem.creator(value));
    }

    /**
     * ID가 있는 저자를 추가합니다.
     *
     * @param id    저자 메타데이터 ID
     * @param value 저자명
     * @return 현재 메타데이터
     */
    public EpubMetadata addCreator(
            String id,
            String value
    ) {
        return add(EpubMetadataItem.creator(id, value));
    }

    /**
     * 저자와 MARC 관계어 역할을 함께 추가합니다.
     *
     * @param id       저자 메타데이터 ID
     * @param value    저자명
     * @param roleCode MARC 관계어 코드
     * @return 현재 메타데이터
     */
    public EpubMetadata addCreator(
            String id,
            String value,
            String roleCode
    ) {
        add(EpubMetadataItem.creator(id, value));
        add(EpubMetadataItem.role(id, roleCode));

        return this;
    }

    /**
     * 저자, 역할 및 정렬명을 함께 추가합니다.
     *
     * @param id       저자 메타데이터 ID
     * @param value    표시 저자명
     * @param roleCode MARC 관계어 코드
     * @param fileAs   정렬용 저자명
     * @return 현재 메타데이터
     */
    public EpubMetadata addCreator(
            String id,
            String value,
            String roleCode,
            String fileAs
    ) {
        addCreator(id, value, roleCode);
        add(EpubMetadataItem.fileAs(id, fileAs));

        return this;
    }

    /**
     * 언어를 추가합니다.
     *
     * @param languageTag BCP 47 언어 태그
     * @return 현재 메타데이터
     */
    public EpubMetadata addLanguage(String languageTag) {
        return add(EpubMetadataItem.language(languageTag));
    }

    /**
     * 출판사를 추가합니다.
     *
     * @param publisher 출판사
     * @return 현재 메타데이터
     */
    public EpubMetadata addPublisher(String publisher) {
        return add(EpubMetadataItem.publisher(publisher));
    }

    /**
     * 주제를 추가합니다.
     *
     * @param subject 주제
     * @return 현재 메타데이터
     */
    public EpubMetadata addSubject(String subject) {
        return add(EpubMetadataItem.subject(subject));
    }

    /**
     * 설명을 추가합니다.
     *
     * @param description 설명
     * @return 현재 메타데이터
     */
    public EpubMetadata addDescription(String description) {
        return add(EpubMetadataItem.description(description));
    }

    /**
     * 저작권 및 이용 권리 정보를 추가합니다.
     *
     * @param rights 권리 정보
     * @return 현재 메타데이터
     */
    public EpubMetadata addRights(String rights) {
        return add(EpubMetadataItem.rights(rights));
    }

    /**
     * 출판일을 추가합니다.
     *
     * @param date 출판일
     * @return 현재 메타데이터
     */
    public EpubMetadata addDate(String date) {
        return add(EpubMetadataItem.date(date));
    }

    /**
     * 수정일 메타데이터를 설정합니다.
     *
     * <p>기존 {@code dcterms:modified} 항목을 제거하고 새 항목을
     * 하나만 추가합니다.</p>
     *
     * @param modified 수정일 문자열
     * @return 현재 메타데이터
     */
    public EpubMetadata setModified(String modified) {
        validateModifiedDate(modified);

        removeByPredicate(EpubMetadataItem::isModifiedDate);
        add(EpubMetadataItem.modified(normalizeModifiedDate(modified)));

        return this;
    }

    /**
     * 지정한 Instant를 EPUB 수정일로 설정합니다.
     *
     * @param instant 수정 시각
     * @return 현재 메타데이터
     */
    public EpubMetadata setModified(Instant instant) {
        Objects.requireNonNull(
                instant,
                "Modified instant must not be null."
        );

        return setModified(
                DateTimeFormatter.ISO_INSTANT.format(instant)
        );
    }

    /**
     * 현재 시각을 수정일로 설정합니다.
     *
     * @return 현재 메타데이터
     */
    public EpubMetadata touch() {
        return setModified(Instant.now());
    }

    /**
     * 특정 대상에 refinement를 추가합니다.
     *
     * @param targetId refinement 대상 ID
     * @param property property
     * @param value    값
     * @return 현재 메타데이터
     */
    public EpubMetadata addRefinement(
            String targetId,
            String property,
            String value
    ) {
        return add(
                EpubMetadataItem.meta(property, value)
                        .refines(targetId)
                        .build()
        );
    }

    /**
     * 특정 대상에 scheme이 포함된 refinement를 추가합니다.
     *
     * @param targetId refinement 대상 ID
     * @param property property
     * @param value    값
     * @param scheme   값 해석 체계
     * @return 현재 메타데이터
     */
    public EpubMetadata addRefinement(
            String targetId,
            String property,
            String value,
            String scheme
    ) {
        return add(
                EpubMetadataItem.meta(property, value)
                        .refines(targetId)
                        .scheme(scheme)
                        .build()
        );
    }

    /**
     * 대상과 property에 해당하는 refinement 값이 존재하는지 확인합니다.
     *
     * @param targetId 대상 메타데이터 ID
     * @param property property
     * @param value    기대값
     * @return 존재하면 {@code true}
     */
    public boolean hasRefinementValue(
            String targetId,
            String property,
            String value
    ) {
        if (value == null) {
            return false;
        }

        return findRefinements(targetId, property)
                .stream()
                .anyMatch(entry ->
                        entry.getValue().equalsIgnoreCase(value.trim())
                );
    }

    /**
     * EPUB 버전을 기준으로 메타데이터를 검증합니다.
     *
     * @param version EPUB 버전
     */
    public void validate(EpubVersion version) {
        Objects.requireNonNull(
                version,
                "EPUB version must not be null."
        );

        validate();

        for (EpubMetadataItem entry : entries) {
            entry.validate(version);
        }

        if (version.isEpub3()) {
            validateEpub3RequiredMetadata();
        } else {
            validateEpub2RequiredMetadata();
        }
    }

    /**
     * 메타데이터 전체 참조와 기본 무결성을 검증합니다.
     */
    public void validate() {
        validateDuplicateIds();
        validateRefinementTargets();
        validateUniqueIdentifier();
        validateSingleModifiedEntry();
    }

    /**
     * 필수 메타데이터가 존재하는지 확인합니다.
     *
     * @param version EPUB 버전
     * @return 필수 메타데이터가 모두 있으면 {@code true}
     */
    public boolean hasRequiredMetadata(EpubVersion version) {
        if (version == null) {
            return false;
        }

        try {
            validate(version);
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    /**
     * 현재 메타데이터의 독립 복사본을 생성합니다.
     *
     * <p>{@link EpubMetadataItem}는 불변 객체이므로 각 항목은
     * 공유합니다.</p>
     *
     * @return 복사된 메타데이터
     */
    public EpubMetadata copy() {
        EpubMetadata copied = new EpubMetadata(entries);

        if (uniqueIdentifierId != null) {
            copied.setUniqueIdentifierId(uniqueIdentifierId);
        }

        return copied;
    }

    private void validateEpub3RequiredMetadata() {
        requireAtLeastOneTitle();
        requireAtLeastOneLanguage();
        requireUniqueIdentifier();
        requireModifiedDate();

        String modifiedValue = getModifiedValue().orElseThrow();
        validateModifiedDate(modifiedValue);
    }

    private void validateEpub2RequiredMetadata() {
        requireAtLeastOneTitle();
        requireAtLeastOneLanguage();
        requireUniqueIdentifier();
    }

    private void requireAtLeastOneTitle() {
        if (getTitles().isEmpty()) {
            throw new IllegalStateException(
                    "EPUB metadata requires at least one dc:title."
            );
        }
    }

    private void requireAtLeastOneLanguage() {
        if (getLanguages().isEmpty()) {
            throw new IllegalStateException(
                    "EPUB metadata requires at least one dc:language."
            );
        }
    }

    private void requireUniqueIdentifier() {
        if (uniqueIdentifierId == null) {
            throw new IllegalStateException(
                    "EPUB package requires a unique-identifier reference."
            );
        }

        EpubMetadataItem identifier = entriesById.get(
                uniqueIdentifierId
        );

        if (identifier == null || !identifier.isIdentifier()) {
            throw new IllegalStateException(
                    "EPUB unique-identifier must reference "
                            + "an existing dc:identifier: "
                            + uniqueIdentifierId
            );
        }
    }

    private void requireModifiedDate() {
        if (getModifiedEntry().isEmpty()) {
            throw new IllegalStateException(
                    "EPUB 3 metadata requires one "
                            + "dcterms:modified entry."
            );
        }
    }

    private void validateDuplicateId(
            EpubMetadataItem entry
    ) {
        entry.getId().ifPresent(id -> {
            if (entriesById.containsKey(id)) {
                throw new IllegalArgumentException(
                        "Duplicate EPUB metadata id: " + id
                );
            }
        });
    }

    private void validateDuplicateIds() {
        Map<String, Integer> counts = new LinkedHashMap<>();

        for (EpubMetadataItem entry : entries) {
            entry.getId().ifPresent(id ->
                    counts.merge(id, 1, Integer::sum)
            );
        }

        List<String> duplicateIds = counts.entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .map(Map.Entry::getKey)
                .toList();

        if (!duplicateIds.isEmpty()) {
            throw new IllegalStateException(
                    "Duplicate EPUB metadata ids: "
                            + String.join(", ", duplicateIds)
            );
        }
    }

    private void validateRefinementTargets() {
        for (EpubMetadataItem entry : entries) {
            entry.getRefinedTargetId().ifPresent(targetId -> {
                if (!entriesById.containsKey(targetId)) {
                    throw new IllegalStateException(
                            "EPUB metadata refinement target not found: "
                                    + entry.getProperty().orElse("unknown")
                                    + " -> "
                                    + targetId
                    );
                }
            });
        }
    }

    private void validateUniqueIdentifier() {
        if (uniqueIdentifierId == null) {
            return;
        }

        EpubMetadataItem identifier =
                entriesById.get(uniqueIdentifierId);

        if (identifier == null) {
            throw new IllegalStateException(
                    "EPUB unique identifier metadata not found: "
                            + uniqueIdentifierId
            );
        }

        if (!identifier.isIdentifier()) {
            throw new IllegalStateException(
                    "EPUB unique identifier must reference "
                            + "a dc:identifier entry: "
                            + uniqueIdentifierId
            );
        }
    }

    private void validateSingleModifiedEntry() {
        List<EpubMetadataItem> modifiedEntries =
                filter(EpubMetadataItem::isModifiedDate);

        if (modifiedEntries.size() > 1) {
            throw new IllegalStateException(
                    "EPUB metadata must not contain multiple "
                            + "dcterms:modified entries."
            );
        }

        if (modifiedEntries.size() == 1) {
            validateModifiedDate(
                    modifiedEntries.get(0).getValue()
            );
        }
    }

    private void removeByPredicate(
            Predicate<EpubMetadataItem> predicate
    ) {
        List<EpubMetadataItem> removing = entries.stream()
                .filter(predicate)
                .toList();

        for (EpubMetadataItem entry : removing) {
            remove(entry);
        }
    }

    private static void validateModifiedDate(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "EPUB modified date must not be blank."
            );
        }

        String normalized = value.trim();

        try {
            OffsetDateTime parsed =
                    OffsetDateTime.parse(normalized);

            if (!ZoneOffset.UTC.equals(parsed.getOffset())) {
                throw new IllegalArgumentException(
                        "EPUB dcterms:modified must use UTC offset Z: "
                                + value
                );
            }
        } catch (DateTimeParseException exception) {
            throw new IllegalArgumentException(
                    "Invalid EPUB dcterms:modified value: "
                            + value
                            + ". Expected format: "
                            + "yyyy-MM-dd'T'HH:mm:ss'Z'",
                    exception
            );
        }
    }

    private static String normalizeModifiedDate(String value) {
        OffsetDateTime parsed =
                OffsetDateTime.parse(value.trim());

        return DateTimeFormatter.ISO_INSTANT.format(
                parsed.toInstant()
        );
    }

    private static String normalizeLookupValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        return value.trim();
    }

    private static String normalizeOptionalIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        String normalized = value.trim();

        if (!isValidIdentifier(normalized)) {
            throw new IllegalArgumentException(
                    "Invalid EPUB metadata identifier: " + value
            );
        }

        return normalized;
    }

    private static boolean isValidIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return false;
        }

        char first = value.charAt(0);

        if (!(Character.isLetter(first) || first == '_')) {
            return false;
        }

        for (int index = 1; index < value.length(); index++) {
            char character = value.charAt(index);

            if (!(Character.isLetterOrDigit(character)
                    || character == '_'
                    || character == '-'
                    || character == '.')) {
                return false;
            }
        }

        return true;
    }

    @Override
    public String toString() {
        return "EpubMetadata{"
                + "entryCount=" + entries.size()
                + ", uniqueIdentifierId='"
                + uniqueIdentifierId + '\''
                + ", title='"
                + getPrimaryTitleValue().orElse(null) + '\''
                + ", language='"
                + getPrimaryLanguage().orElse(null) + '\''
                + ", modified='"
                + getModifiedValue().orElse(null) + '\''
                + '}';
    }

    /**
     * {@link EpubMetadata} 생성 Builder입니다.
     */
    public static final class Builder {

        private final List<EpubMetadataItem> entries =
                new ArrayList<>();

        private String uniqueIdentifierId;

        private EpubVersion version;

        private boolean validateOnBuild = true;

        private boolean autoModified;

        private Instant modifiedInstant;

        private Builder() {
        }

        public Builder entry(EpubMetadataItem entry) {
            entries.add(
                    Objects.requireNonNull(
                            entry,
                            "EPUB metadata entry must not be null."
                    )
            );

            return this;
        }

        public Builder entries(
                Collection<EpubMetadataItem> entries
        ) {
            if (entries == null) {
                return this;
            }

            for (EpubMetadataItem entry : entries) {
                entry(entry);
            }

            return this;
        }

        /**
         * 고유 식별자를 추가하고 package unique-identifier로 설정합니다.
         *
         * @param id    식별자 메타데이터 ID
         * @param value 식별자 값
         * @return 현재 Builder
         */
        public Builder uniqueIdentifier(
                String id,
                String value
        ) {
            entry(EpubMetadataItem.identifier(id, value));
            this.uniqueIdentifierId = id;

            return this;
        }

        public Builder uniqueIdentifierId(
                String uniqueIdentifierId
        ) {
            this.uniqueIdentifierId = uniqueIdentifierId;
            return this;
        }

        public Builder title(String title) {
            return entry(EpubMetadataItem.title(title));
        }

        public Builder title(
                String id,
                String title
        ) {
            return entry(EpubMetadataItem.title(id, title));
        }

        public Builder mainTitle(
                String id,
                String title
        ) {
            entry(EpubMetadataItem.title(id, title));

            entry(
                    EpubMetadataItem.meta("title-type", "main")
                            .refines(id)
                            .build()
            );

            return this;
        }

        public Builder creator(String creator) {
            return entry(EpubMetadataItem.creator(creator));
        }

        public Builder creator(
                String id,
                String creator
        ) {
            return entry(
                    EpubMetadataItem.creator(id, creator)
            );
        }

        public Builder creator(
                String id,
                String creator,
                String roleCode
        ) {
            entry(EpubMetadataItem.creator(id, creator));
            entry(EpubMetadataItem.role(id, roleCode));

            return this;
        }

        public Builder creator(
                String id,
                String creator,
                String roleCode,
                String fileAs
        ) {
            creator(id, creator, roleCode);
            entry(EpubMetadataItem.fileAs(id, fileAs));

            return this;
        }

        public Builder language(String languageTag) {
            return entry(
                    EpubMetadataItem.language(languageTag)
            );
        }

        public Builder publisher(String publisher) {
            return entry(
                    EpubMetadataItem.publisher(publisher)
            );
        }

        public Builder subject(String subject) {
            return entry(EpubMetadataItem.subject(subject));
        }

        public Builder description(String description) {
            return entry(
                    EpubMetadataItem.description(description)
            );
        }

        public Builder rights(String rights) {
            return entry(EpubMetadataItem.rights(rights));
        }

        public Builder date(String date) {
            return entry(EpubMetadataItem.date(date));
        }

        public Builder modified(String modified) {
            return entry(EpubMetadataItem.modified(modified));
        }

        public Builder modified(Instant instant) {
            Objects.requireNonNull(
                    instant,
                    "Modified instant must not be null."
            );

            return modified(
                    DateTimeFormatter.ISO_INSTANT.format(instant)
            );
        }

        /**
         * build 시 현재 시각으로 dcterms:modified를 자동 생성합니다.
         *
         * @param autoModified 자동 생성 여부
         * @return 현재 Builder
         */
        public Builder autoModified(boolean autoModified) {
            this.autoModified = autoModified;
            return this;
        }

        /**
         * 자동 수정일 생성에 사용할 고정 시각을 설정합니다.
         *
         * <p>테스트에서 재현 가능한 결과가 필요할 때 사용합니다.</p>
         *
         * @param modifiedInstant 수정 시각
         * @return 현재 Builder
         */
        public Builder modifiedInstant(
                Instant modifiedInstant
        ) {
            this.modifiedInstant = modifiedInstant;
            return this;
        }

        public Builder version(EpubVersion version) {
            this.version = version;
            return this;
        }

        public Builder validateOnBuild(
                boolean validateOnBuild
        ) {
            this.validateOnBuild = validateOnBuild;
            return this;
        }

        public EpubMetadata build() {
            EpubMetadata metadata = new EpubMetadata(entries);

            if (uniqueIdentifierId != null) {
                metadata.setUniqueIdentifierId(
                        uniqueIdentifierId
                );
            }

            if (autoModified
                    && metadata.getModifiedEntry().isEmpty()) {
                metadata.setModified(
                        modifiedInstant == null
                                ? Instant.now()
                                : modifiedInstant
                );
            }

            if (validateOnBuild) {
                if (version == null) {
                    metadata.validate();
                } else {
                    metadata.validate(version);
                }
            }

            return metadata;
        }
    }
}