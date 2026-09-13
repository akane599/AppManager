# Main-list sorting and filtering

The main app list includes **Debloatable (UAD)** and four independent rating chips:
**UAD [Recommended]**, **UAD [Advanced]**, **UAD [Expert]**, and **UAD [Unsafe]**.
Selecting several ratings includes any of those ratings. Existing filters, search,
users, and the selected profile further narrow the result. For example, select
**System apps + Running apps + UAD [Recommended] + UAD [Advanced]** to show running
system apps in either of those two UAD categories.

The Debloatable chip includes all packages in the bundled UAD dataset, including
Unsafe packages. Selecting a rating narrows it. Unknown ratings remain Unsafe;
apps absent from the dataset do not match any UAD filter. Filtering does not
select apps for removal or change their permissions. Existing contradictory
filters still require every selected condition; for example, With trackers and
Without trackers together produce no matches.

Additional filters expose **With trackers**, **Without trackers**, **Without
activities**, **Updated system apps**, **Debuggable apps**, and **Persistent apps**.
Tracker filters use the same cached counts displayed and sorted in the main list.
Without activities means no activity components, including non-launcher activities.
Persistent is Android's persistent application flag, distinct from currently running.

New sorting options:

| Option | Default order |
| --- | --- |
| Debloat rating | Recommended, Advanced, Expert, Unsafe, then unlisted apps |
| Latest backup | Newest backup first; apps without a backup last |
| Version code | Highest numeric version code first |
| App size | Largest cached code + OBB size first |
| App data size | Largest cached data + media + cache size first |

Reverse reverses the primary order. Ties stay alphabetical, with package names
breaking identical-label ties. Size options use the existing usage-access feature
and database measurements, aggregated across installed users like Total size;
unavailable measurements remain zero. They do not trigger new storage queries.
Running apps retains the existing backend and permission requirements.

The persisted sort IDs and filter bits are appended, so existing preferences keep
their meaning. UAD metadata uses a shared package-name index. Missing SDK/signature
values sort consistently, and refreshing the options sheet no longer saves an
invalid sort ID. Reverse and sort settings are saved before scheduling the work.

Regression coverage includes every nonempty UAD rating combination with Running
and System filters, unlisted/unknown ratings, cached tracker counts, extra app types,
activity absence, preference and filter serialization, the bundled metadata index,
sort order/reversal/ties, comparator contracts with absent metadata, and UI chip
selection/refresh.
