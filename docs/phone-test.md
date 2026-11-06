# Manual phone test checklist

Perform this only after the owner has deployed and run the remote smoke test. Use a real vendor phone and a separate customer phone on mobile data. The payment gateway is currently the mock; no real payment is sent.

- [ ] Open the vendor board on the vendor phone. Before starting, tap **Enable sound** and confirm the browser permits audio/speech.
- [ ] Print the vendor's QR card at its intended size. Scan it from the customer phone and confirm the browser opens the Vercel `/v/{slug}` page after the services have been idle.
- [ ] Enter the amount and create an order. Confirm the vendor board's **Scanned** counter changes without a refresh and a new paying row appears.
- [ ] Confirm the customer sees the short queue token and the vendor board shows the matching amber row.
- [ ] Continue through the mock checkout. Confirm the vendor row turns green without refreshing, the customer status says **Verified**, and the spoken line is audible on the vendor phone.
- [ ] On a separate order, leave payment incomplete and wait for the configured real expiry. Confirm the vendor row turns red and the customer page says the token expired.
- [ ] Change the customer page to Hindi and confirm the visible labels/status copy change. Check the vendor board's Hindi labels as well.
- [ ] Enable glance mode and check that status words/icons remain understandable in outdoor sunlight at arm's length.
- [ ] Re-open the printed QR in a fresh browser tab and verify it encodes the production Vercel URL, not localhost or a Render hostname.
- [ ] Repeat after the Render services have slept for at least 15 minutes. Record cold-start time, failures, reconnect behavior, and whether any action needs to be retried. Do not press pay twice if the first request is still pending.
- [ ] Record device model, OS/browser version, network type, date/time, screenshots with customer references redacted, and each result. Do not record OTPs, invite codes, tokens, or payment credentials.

Report failures before sharing the QR with customers. A phone test does not replace a real gateway/sandbox integration, field vendor survey, or a review of the 30-day free database lifecycle.
