/**
 * Media module — manages upload, storage, retrieval, and lifecycle of all media files
 * across the SocialWallet application.
 *
 * <p>Uses imgproxy for on-the-fly image transformation (resize, crop, format conversion)
 * and DigitalOcean Spaces (S3-compatible) for object storage. Only the original file
 * is stored — no pre-computed variants.</p>
 *
 * <p>Access control is delegated to calling modules (Profiles, Stories, Chat, Store).
 * Media only enforces uploader ownership for download and delete operations.</p>
 *
 * @see com.socialwallet.media.service.MediaService
 * @see com.socialwallet.media.service.UrlSigningService
 */
package com.socialwallet.media;