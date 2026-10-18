package com.barbershop.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Public address and contact data of a shop (website footer, schema.org JSON-LD).
 * Every field is optional; empty values are simply left out of the structured data.
 */
@Embeddable
public class ShopProfile {

    @Column(name = "profile_street")
    private String street;

    @Column(name = "profile_postal_code")
    private String postalCode;

    @Column(name = "profile_city")
    private String city;

    /** ISO 3166-1 alpha-2, e.g. "DE". */
    @Column(name = "profile_country")
    private String country;

    @Column(name = "profile_phone")
    private String phone;

    @Column(name = "profile_email")
    private String email;

    /** schema.org priceRange, e.g. "€€". */
    @Column(name = "profile_price_range")
    private String priceRange;

    public String getStreet() { return street; }
    public void setStreet(String street) { this.street = street; }
    public String getPostalCode() { return postalCode; }
    public void setPostalCode(String postalCode) { this.postalCode = postalCode; }
    public String getCity() { return city; }
    public void setCity(String city) { this.city = city; }
    public String getCountry() { return country; }
    public void setCountry(String country) { this.country = country; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPriceRange() { return priceRange; }
    public void setPriceRange(String priceRange) { this.priceRange = priceRange; }
}
