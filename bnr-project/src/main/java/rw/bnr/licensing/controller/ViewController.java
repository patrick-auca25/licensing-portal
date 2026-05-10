package rw.bnr.licensing.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

/**
 * ViewController — serves all Thymeleaf HTML pages.
 *
 * These routes are STATELESS — no JWT required to load the HTML.
 * The page JS checks Auth.isLoggedIn() and redirects to /login if needed.
 * All data is loaded via /api/** calls which ARE JWT-protected.
 *
 * The sidebar user info (name, role) comes from sessionStorage set at login.
 * Thymeleaf session attributes are NOT used — everything is JS-driven.
 */
@Controller
public class ViewController {

    @GetMapping({"/", "/login"})
    public String login() { return "auth/login"; }

    @GetMapping("/logout")
    public String logout() { return "redirect:/login"; }

    // ── Applicant pages ──────────────────────────────────────────────────
    @GetMapping("/applicant/dashboard")
    public String applicantDashboard() { return "applicant/dashboard"; }

    @GetMapping("/applicant/applications/new")
    public String newApplication() { return "applicant/new-application"; }

    @GetMapping("/applicant/applications/{id}")
    public String applicantAppDetail(@PathVariable UUID id) {
        return "applicant/application-detail";
    }

    // ── Reviewer pages ───────────────────────────────────────────────────
    @GetMapping("/reviewer/dashboard")
    public String reviewerDashboard() { return "reviewer/dashboard"; }

    @GetMapping("/reviewer/applications/{id}")
    public String reviewerAppDetail(@PathVariable UUID id) {
        return "applicant/application-detail";
    }

    // ── Approver pages ───────────────────────────────────────────────────
    @GetMapping("/approver/dashboard")
    public String approverDashboard() { return "approver/dashboard"; }

    @GetMapping("/approver/applications/{id}")
    public String approverAppDetail(@PathVariable UUID id) {
        return "applicant/application-detail";
    }

    // ── Admin pages ──────────────────────────────────────────────────────
    @GetMapping("/admin/dashboard")
    public String adminDashboard() { return "admin/dashboard"; }

    @GetMapping("/admin/applications/{id}")
    public String adminAppDetail(@PathVariable UUID id) {
        return "applicant/application-detail";
    }
}