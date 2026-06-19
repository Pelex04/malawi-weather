package mw.pelex.weatherapi.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * FIX B7: Added @Size constraints on every field.
 * Without these, a malicious actor could send megabyte-sized strings
 * that saturate the JPA layer or the DB column width.
 *
 * Limits are generous for legitimate users but block abuse.
 */
public class DeveloperRegistrationRequest {

    @NotBlank(message = "Name is required")
    @Size(min = 2, max = 100, message = "Name must be between 2 and 100 characters")
    private String name;

    @NotBlank(message = "Email is required")
    @Email(message = "Must be a valid email address")
    @Size(max = 255, message = "Email must not exceed 255 characters")
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 128, message = "Password must be between 8 and 128 characters")
    private String password;

    @NotBlank(message = "App name is required")
    @Size(min = 2, max = 150, message = "App name must be between 2 and 150 characters")
    private String appName;

    @Size(max = 1000, message = "App description must not exceed 1000 characters")
    private String appDescription;

    @Size(max = 150, message = "Organisation name must not exceed 150 characters")
    private String organization;

    // ── Getters & setters ─────────────────────────────────────────────────────

    public String getName()           { return name; }
    public void   setName(String v)   { this.name = v; }

    public String getEmail()          { return email; }
    public void   setEmail(String v)  { this.email = v; }

    public String getPassword()       { return password; }
    public void   setPassword(String v){ this.password = v; }

    public String getAppName()        { return appName; }
    public void   setAppName(String v){ this.appName = v; }

    public String getAppDescription()         { return appDescription; }
    public void   setAppDescription(String v) { this.appDescription = v; }

    public String getOrganization()           { return organization; }
    public void   setOrganization(String v)   { this.organization = v; }
}
