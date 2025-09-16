# **FileShare - Anonymous File Sharing Tool**
### Live Demo - [share.nathanasowata.com](https://share.nathanasowata.com)

FileShare is a minimalist, secure, and temporary file and text sharing web application. It allows users to upload content without an account, receive a unique sharable link, and have that content automatically deleted after 24 hours.

### **Key Features**

*   **Account-Free Uploads:** No registration required to upload files or text.
*   **Secure Links:** A cryptographically secure, random ID is generated for each upload.
*   **Automatic Expiration:** All content is permanently deleted from storage and the database 24 hours after creation.
*   **Privacy Focused:** No user tracking and minimal metadata storage.
*   **Clean UI:** A modern, responsive interface inspired by Microsoft's Fluent Design.
*   **File & Text Support:** Supports both file uploads (up to 25 MB) and text snippets (up to 100,000 characters).

---

### **Technology Stack**

| Component      | Technology                                                                                             |
| :------------- | :----------------------------------------------------------------------------------------------------- |
| **Frontend**   | React 18, TypeScript, Vite, Axios, React Router                                                        |
| **Backend**    | Java 21, Spring Boot 3, Spring Data JPA                                                                |
| **Database**   | PostgreSQL 16                                                                                          |
| **Storage**    | Cloudflare R2 (S3-Compatible Object Storage)                                                           |
| **Deployment** | Docker, AWS EC2 (t4g.small), Vercel (Frontend CDN), Caddy (Reverse Proxy & SSL)                        |

---

### **System Architecture**

```
+----------------+      +-------------------------+      +-----------------------+
|                |      |                         |      |                       |
|  User Browser  |----->|   Vercel Global CDN     |----->|    Frontend (React)   |
|                |      | (share.nathanasowata.com) |      |                       |
+----------------+      +-------------------------+      +-----------------------+
       |                                                            |
       | HTTPS Request (Upload/Download)                            |
       |                                                            |
       v                                                            v
+-------------------------------+         +--------------------------+      +--------------------------+
|                               |         |      AWS EC2 Instance    |      |                          |
|               API             |<--------|  Caddy Reverse Proxy     |----->|        Backend           |
|                               |         |                          |      |      (Spring Boot)       |
| (api-share.nathanasowata.com) |         |  (Port 443 -> 8080)      |      |    (Docker Container)    |
+-------------------------------+         +--------------------------+      +--------------------------+
                                                                              /              \
                                                                            /                 \
                                                        +------------------+     +--------------------+
                                                        | Cloudflare R2    |     | PostgreSQL DB      |
                                                        | (File Storage)   |     | (Metadata)         |
                                                        |                  |     | (Docker Container) |
                                                        +------------------+     +--------------------+
```

---

### **Technical Summary**

This section details the architecture and problem-solving process behind FileShare, a globally deployed, privacy-first file sharing application. My goal was to deliver a production-grade application by implementing a modern, secure, and cost-effective full-stack architecture.

The core architectural pillars are:
*   A **Containerized Java Backend** for consistent, reproducible deployments.
*   A **Static CDN-Hosted Frontend** for global low-latency and performance.
*   A **Secure Reverse Proxy** for robust, automated SSL/TLS management.

#### **Architectural Decisions & Direct Impact**

*   #### **Backend: Spring Boot 3 & Java 21**
    *   **Direct Impact:** Used Spring Data JPA to generate the entire data access layer from the `UploadRepository` interface. Methods like `findByShortId` and `findByExpiresAtBefore` required zero implementation, directly accelerating the development of the metadata endpoint and the core cleanup logic.
    *   **Direct Impact:** Integrated the OWASP Java HTML Sanitizer library into the `StorageService`'s upload logic. This specifically mitigates XSS risks by stripping all HTML from text snippets before they are stored, a critical security measure for a public content-hosting platform.

*   #### **Frontend: React, TypeScript & Vite**
    *   **Direct Impact:** Defined a strict TypeScript interface for the `MetadataResponse` from `/api/v1/meta/{shortId}`. This prevented runtime errors in the `DownloadPage` component by enforcing a check for the optional `textContent` field before attempting to render it.
    *   **Direct Impact:** Employed `react-dropzone` to handle file input. This offloaded complex drag-and-drop state management, browser compatibility issues, and file validation logic, allowing focus to remain on the core upload functionality.

*   #### **Storage: Cloudflare R2 & PostgreSQL**
    *   **Direct Impact:** Chose Cloudflare R2 specifically for its zero-cost data egress. This was the key financial decision that makes offering a 25 MB file download service to the public sustainable, as AWS S3's per-gigabyte egress fees would make the service prohibitively expensive.
    *   **Direct Impact:** Leveraged PostgreSQL’s native `TIMESTAMPTZ` data type for the `expires_at` column. This enabled the `@Scheduled` `CleanupService` to run a simple, time zone-agnostic query (`WHERE expires_at < NOW()`) to enforce the 24-hour deletion guarantee with absolute reliability.

*   #### **Deployment: Docker, EC2 & Caddy**
    *   **Direct Impact:** Implemented Caddy as a reverse proxy to resolve the browser's `net::ERR_SSL_PROTOCOL_ERROR`. It terminates public HTTPS traffic for `api-share.nathanasowata.com` and forwards it as plain HTTP to the Spring Boot container on port 8080, cleanly solving the mixed-content issue between the Vercel frontend and the EC2 backend.
    *   **Direct Impact:** Connected the `fileshare-app` and `fileshare-db` containers on a custom Docker network. This allowed the Spring `datasource.url` to use the stable DNS name `fileshare-db` instead of a brittle IP address, ensuring the connection remains stable across container restarts.

#### **Production Deployment: Problems & Solutions**

Execution required solving three distinct, production-critical issues.

*   **Problem 1: Insecure Production Configuration**
    *   **Challenge:** Securely providing R2 API keys and a stable DB connection string to the production container without hardcoding secrets or relying on fragile IP addresses.
    *   **Solution:** Injected secrets at runtime by mounting a `production.env` file into the container via the `docker run --env-file` flag. Spring Boot automatically detects and uses these variables, ensuring a clean separation of configuration from the Docker image. This completely avoids committing secrets to version control.

*   **Problem 2: Single-Page Application (SPA) Routing Failure**
    *   **Challenge:** Direct navigation to a shared link (e.g., `/view/some-id`) resulted in a Vercel `404 NOT FOUND` error because the static file server could not find a corresponding HTML file.
    *   **Solution:** Created a `vercel.json` file with a `rewrite` rule. This rule intercepts all incoming requests that aren't for static assets (like CSS or JS) and redirects them to `index.html`. This passes control to the client-side React Router, which correctly parses the URL and renders the `DownloadPage` component.

*   **Problem 3: SSL Certificate Provisioning Failure**
    *   **Challenge:** Caddy's automated Let's Encrypt process failed its ACME HTTP-01 challenge, preventing it from issuing an SSL certificate for the API.
    *   **Solution:** Diagnosed the failure by inspecting Caddy's logs (`journalctl -u caddy`). The root cause was a misconfigured DNS `A` record and a missing firewall rule. The fix involved correcting the subdomain from `api.share` to `api-share` in both the DNS provider and the `Caddyfile`, and adding an inbound rule to the AWS Security Group to allow public traffic on port 80, which is required by the Let's Encrypt validation servers.

The final deployed application is secure, cost-effective, and globally performant, validating the architectural choices and demonstrating an end-to-end execution of a production-grade system.


### Running Locally (Docker - Recommended)

The easiest and most reliable way to run the backend and its database is with Docker.

#### **Prerequisites**

-   Docker
-   Docker Compose

#### **Instructions**

1.  **Clone the repository:**
    ```bash
    git clone https://github.com/NathanAsowata/file-share.git
    cd file-share
    ```

2.  **Configure Local Secrets:**
    The application requires credentials for the R2 bucket. Create a `docker-compose.override.yml` file to provide these secrets locally. This file is included in `.gitignore` and will not be committed.
    ```bash
    touch docker-compose.override.yml
    ```
    Paste the following content into the file, replacing the placeholder values with your actual R2 credentials:
    ```yaml
    version: '3.8'
    services:
      app:
        environment:
          - AWS_S3_ENDPOINT_URL=https://<YOUR_ACCOUNT_ID>.r2.cloudflarestorage.com
          - AWS_S3_ACCESS_KEY_ID=<YOUR_R2_ACCESS_KEY_ID>
          - AWS_S3_SECRET_ACCESS_KEY=<YOUR_R2_SECRET_ACCESS_KEY>
          - AWS_S3_BUCKET_NAME=<YOUR_R2_BUCKET_NAME>
    ```

3.  **Build and Run the Containers:**
    This command will build the Spring Boot application image and start both the `app` and `db` containers.
    ```bash
    docker-compose up --build
    ```
    The API will be available at `http://localhost:8080`.

### Testing the API

You can test the API in two ways:

1.  **With the Frontend:** The recommended method for end-to-end testing. Follow the setup instructions in the [file-share-web repository](https://github.com/NathanAsowata/file-share-web).
2.  **With a `curl` Command:** To test the text upload endpoint directly:
    ```bash
    curl -X POST -F "text=Hello, this is a test snippet" http://localhost:8080/api/v1/upload
    ```

### Production Environment Variables

The application is configured via the following environment variables.

| Variable                       | Description                                                     | Example                                                              |
| :----------------------------- | :-------------------------------------------------------------- | :------------------------------------------------------------------- |
| `SPRING_DATASOURCE_URL`        | The JDBC URL for the PostgreSQL container.                      | `jdbc:postgresql://fileshare-db:5432/fileshare`                      |
| `SPRING_DATASOURCE_USERNAME`   | The username for the PostgreSQL database.                       | `user`                                                               |
| `SPRING_DATASOURCE_PASSWORD`   | The password for the PostgreSQL database.                       | `password`                                                           |
| `APP_DOMAIN`                   | The public domain of the frontend app (for generating links).   | `https://share.nathanasowata.com`                                    |
| `AWS_S3_ENDPOINT_URL`          | The full endpoint URL for the Cloudflare R2 bucket.               | `https://<ACCOUNT_ID>.r2.cloudflarestorage.com`                        |
| `AWS_S3_ACCESS_KEY_ID`         | The Access Key ID for the R2 bucket.                              | `...`                                                                |
| `AWS_S3_SECRET_ACCESS_KEY`     | The Secret Access Key for the R2 bucket.                          | `...`                                                                |
| `AWS_S3_REGION`                | The region for the R2 bucket (usually `auto`).                  | `auto`                                                               |
| `AWS_S3_BUCKET_NAME`           | The name of the R2 bucket.                                        | `fileshare-prod`                                                     |