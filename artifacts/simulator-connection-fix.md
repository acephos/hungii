# Simulator connection recovery — 0.7.2

The reported 0.7.1 phone failure was accompanied by Tailscale reporting the phone offline. The gateway health and `/api` status checks succeeded on the computer; both the locally built endpoint and GitHub release variable pointed to `100.103.202.33:8788`. A phone outside the tailnet cannot reach this private service. Recovery on the user's physical phone remains for the user to confirm.

The app also let simulator IO failures escape to the generic `Could not complete this request. Try again.` message. The simulator HTTP boundary now converts connection/read failures to an app-owned `HUNGII_DEMO_OFFLINE` error with the destination, running-server requirement, Tailscale instruction and refresh action. Synthetic connection UI no longer requests real Swiggy-token consent or offers account sign-out. Real account/Swiggy connection UI is preserved.

Validation:

- Before the fix, `:app:testDemoDebugUnitTest` failed the closed-server regression with `ConnectException`; the other two transport cases passed.
- After the fix, all three transport tests passed for each Android flavor: unreachable server recovery, successful JSON and preserved gateway error code/message.
- Blocking only simulator traffic on the Android emulator reproduced the original generic error. Updating to 0.7.2 under the same blocked connection displayed the correct destination and Tailscale recovery instructions with no Swiggy-token consent.
- Removing the temporary emulator firewall rule, tapping Refresh connection and Home → Find my next meal loaded Chicken & greens box with shortlist controls. The temporary rule was removed.
- Both APKs built and both Android lint checks passed. Check/release CI now includes these transport regressions.

The simulator still requires the computer and Tailscale connection. This patch explains failures; it does not turn the privately hosted server into an offline Android service.
