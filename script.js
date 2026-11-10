const form = document.getElementById("reportForm");
const reportList = document.getElementById("reportList");

let reports = [];

async function request(url, data) {
    const response = await fetch(url, {
        method: "POST",
        headers: {
            "Content-Type": "application/x-www-form-urlencoded"
        },
        body: new URLSearchParams(data)
    });

    return response.json();
}

function escapeHTML(value) {
    return String(value).replace(/[&<>"']/g, char => ({
        "&": "&amp;",
        "<": "&lt;",
        ">": "&gt;",
        '"': "&quot;",
        "'": "&#39;"
    })[char]);
}

// Create Report
form.addEventListener("submit", async function(e) {
    e.preventDefault();

    const data = {
        type: document.getElementById("type").value,
        item: document.getElementById("item").value.trim(),
        description: document.getElementById("description").value.trim(),
        location: document.getElementById("location").value.trim(),
        date: document.getElementById("date").value,
        contact: document.getElementById("contact").value.trim()
    };

    try {
        const result = await request("/api/create", data);
        alert(result.message);
        form.reset();
        loadReports();
    } catch (error) {
        alert("Could not connect to Java server!");
    }
});

// Display Reports
async function loadReports() {
    try {
        const response = await fetch("/api/reports");
        reports = await response.json();
        displayReports();
    } catch (error) {
        reportList.textContent = "Unable to load reports.";
    }
}

function displayReports() {
    reportList.innerHTML = "";

    const search = document.getElementById("search")
        .value.toLowerCase();

    const type = document.getElementById("filter").value;
    const status = document.getElementById("statusFilter").value;

    reports.forEach((r, index) => {
        if (!r.item.toLowerCase().includes(search) ||
            (type !== "All" && r.type !== type) ||
            (status !== "All" && r.status !== status)) {
            return;
        }

        const card = document.createElement("div");
        card.className = "report-card";

        card.innerHTML = `
            <h3>${escapeHTML(r.item)}</h3>
            <p><b>Type:</b> ${escapeHTML(r.type)}</p>
            <p><b>Description:</b> ${escapeHTML(r.description)}</p>
            <p><b>Location:</b> ${escapeHTML(r.location)}</p>
            <p><b>Date:</b> ${escapeHTML(r.date)}</p>
            <p><b>Contact:</b> ${escapeHTML(r.contact)}</p>
            <p><b>Status:</b>
            <span class="${r.status === "Open" ? "open" : "resolved"}">
            ${escapeHTML(r.status)}</span></p>

            <button data-action="resolve" ${r.status === "Resolved" ? "disabled" : ""}>
                Mark Resolved
            </button>
            <button data-action="contact">Update Contact</button>
        `;

        card.querySelector('[data-action="resolve"]')
            .addEventListener("click", () => markResolved(index));

        card.querySelector('[data-action="contact"]')
            .addEventListener("click", () => updateContact(index));

        reportList.appendChild(card);
    });

    if (reportList.children.length === 0) {
        reportList.textContent = "No reports found.";
    }

    updateStats();
}

// Mark Resolved
async function markResolved(index) {
    try {
        const result = await request("/api/resolve", { index });
        alert(result.message);
        loadReports();
    } catch (error) {
        alert("Update failed!");
    }
}

// Update Contact
async function updateContact(index) {
    const contact = prompt("Enter new contact details:");

    if (!contact || !contact.trim()) return;

    try {
        const result = await request("/api/contact", {
            index,
            contact: contact.trim()
        });

        alert(result.message);
        loadReports();
    } catch (error) {
        alert("Contact update failed!");
    }
}

// Search Item
document.getElementById("search")
    .addEventListener("input", displayReports);

// Filter Lost / Found
document.getElementById("filter")
    .addEventListener("change", displayReports);

// Filter Status
document.getElementById("statusFilter")
    .addEventListener("change", displayReports);

// Dashboard Statistics
function updateStats() {
    document.getElementById("total").textContent = reports.length;

    document.getElementById("lost").textContent =
        reports.filter(r => r.type === "Lost").length;

    document.getElementById("found").textContent =
        reports.filter(r => r.type === "Found").length;

    document.getElementById("resolved").textContent =
        reports.filter(r => r.status === "Resolved").length;
}

// Start
loadReports();