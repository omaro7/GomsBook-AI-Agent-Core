/*
 * Copyright (c) 2026 GomsBook (JungHoon Han)
 * All rights reserved.
 */
package kr.co.goms.gomsbook.ai.epub.generation.copyright;

public class EpubCopyrightPage {

    private String fileName = "copyright.xhtml";

    private String title;
    private String publicationDate;

    private String author;
    private String publisherRepresentative;
    private String publisher;

    private String address;
    private String email;
    private String website;

    private String publishingRegistration;
    private String isbn;
    private String price;

    private String supportText;

    private String copyrightHolder;
    private String copyrightYear;
    private String copyrightText;
    
    private String stylesheetHref;


    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }


    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }


    public String getPublicationDate() {
        return publicationDate;
    }

    public void setPublicationDate(String publicationDate) {
        this.publicationDate = publicationDate;
    }


    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }


    public String getPublisherRepresentative() {
        return publisherRepresentative;
    }

    public void setPublisherRepresentative(String publisherRepresentative) {
        this.publisherRepresentative = publisherRepresentative;
    }


    public String getPublisher() {
        return publisher;
    }

    public void setPublisher(String publisher) {
        this.publisher = publisher;
    }


    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }


    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }


    public String getWebsite() {
        return website;
    }

    public void setWebsite(String website) {
        this.website = website;
    }


    public String getPublishingRegistration() {
        return publishingRegistration;
    }

    public void setPublishingRegistration(String publishingRegistration) {
        this.publishingRegistration = publishingRegistration;
    }


    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }


    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }


    public String getSupportText() {
        return supportText;
    }

    public void setSupportText(String supportText) {
        this.supportText = supportText;
    }


    public String getCopyrightHolder() {
        return copyrightHolder;
    }

    public void setCopyrightHolder(String copyrightHolder) {
        this.copyrightHolder = copyrightHolder;
    }


    public String getCopyrightYear() {
        return copyrightYear;
    }

    public void setCopyrightYear(String copyrightYear) {
        this.copyrightYear = copyrightYear;
    }


    public String getCopyrightText() {
        return copyrightText;
    }

    public void setCopyrightText(String copyrightText) {
        this.copyrightText = copyrightText;
    }
    
    public String getStylesheetHref() {
        return stylesheetHref;
    }

    public void setStylesheetHref(String stylesheetHref) {
        this.stylesheetHref = stylesheetHref;
    }
}