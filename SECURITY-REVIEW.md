# MotionPad pre-release security review

Use a trusted local network for internal testing.

| Priority | Evidence in the project | Impact and recommended change |
| --- | --- | --- |
| High on shared networks | Handset.kt broadcasts the code during discovery and accepts the same value as a response. Settings.kt stores that code without expiration. Router.kt associates the player with an IP address and checks CRC32. | Another device on the network can learn the code and impersonate an endpoint. Motion and button data are sent in plaintext, without per-message authentication or replay protection. Use discovery without secrets, authenticated pairing and an encrypted channel with replay protection between the phone and console. Keep DSU local for Dolphin. |
| Medium, requires another app on the device | The main activity is exported. MotionPadPackage.kt accepts a console-mode flag from the launch or resume intent and can start the service. | A local app could activate the receiver without the user choosing Console. Restrict mode changes to the app's own interface and use a private route to reopen it from the notification. |
| Low, local | Router.kt tracks DSU subscriptions by address and port with a six-second expiration, without a limit on simultaneous subscriptions. | Another local process could increase memory and CPU usage. Limit subscriptions and retain expiration. |

## Dependencies

At the time of this review, React Native 0.76.9 was out of support. Running `npm audit` against the lockfile reported 15 entries: one high, eight moderate and six low, including findings propagated to dependent packages. The high-severity finding concerns image-size 1.2.1, used by Metro. It affects image processing during bundling. Update the tooling and check compatibility. Also repeat the Android dependency review before publishing.

## Scope

This review covered static analysis of the React Native and Kotlin code, the release manifest, signing and npm dependencies. Type checking, both JavaScript tests and all seven Kotlin tests were run, including the local UDP tests. Physical-device testing and an audit of native dependencies remain pending. Private signing material must stay out of the public repository.
