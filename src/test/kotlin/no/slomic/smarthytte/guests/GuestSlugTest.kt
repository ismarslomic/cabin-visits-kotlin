package no.slomic.smarthytte.guests

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class GuestSlugTest :
    StringSpec({
        "slug should be the lowercase name with dots" {
            guestSlug("Ola", "Nordmann") shouldBe "ola.nordmann"
            guestSlug("  Ola  ", "Nordmann-Hansen") shouldBe "ola.nordmann.hansen"
            guestSlug("Ola", "O'Brien Jr.") shouldBe "ola.o.brien.jr"
        }

        "slug should transliterate Norwegian letters and remove diacritics" {
            guestSlug("Ærlig", "Østby") shouldBe "aerlig.ostby"
            guestSlug("Åse", "Bjørn") shouldBe "ase.bjorn"
            guestSlug("Jörg", "Müller") shouldBe "jorg.muller"
            guestSlug("Jürgen", "Kärki") shouldBe "jurgen.karki"
            guestSlug("José", "Núñez") shouldBe "jose.nunez"
        }

        "slug should be guest when no letter or digit is left" {
            guestSlug("!!!", "???") shouldBe "guest"
            guestSlug("日本", "語") shouldBe "guest"
        }

        "slug should be at most 50 characters and not end with a dot" {
            val slug = guestSlug("a".repeat(20), "b".repeat(30))

            slug.length shouldBe 50
            slug.endsWith(".") shouldBe false
            guestSlug("a".repeat(49), "b".repeat(10)) shouldBe "a".repeat(49)
        }

        "id should be the slug when it is free, and get a number on a collision" {
            uniqueGuestId("ola.nordmann", emptyList()) shouldBe "ola.nordmann"
            uniqueGuestId("ola.nordmann", listOf("ola.nordmann")) shouldBe "ola.nordmann-2"
            uniqueGuestId("ola.nordmann", listOf("ola.nordmann", "ola.nordmann-2")) shouldBe "ola.nordmann-3"
        }

        "id collisions should ignore case" {
            uniqueGuestId("ola", listOf("OLA")) shouldBe "ola-2"
        }

        "system should never be used as an id" {
            uniqueGuestId("system", emptyList()) shouldBe "system-2"
            uniqueGuestId("system", listOf("system-2")) shouldBe "system-3"
        }

        "id with a number should stay within 50 characters" {
            val slug = "a".repeat(50)

            val id = uniqueGuestId(slug, listOf(slug))

            id shouldBe "a".repeat(48) + "-2"
            id.length shouldBe 50
            uniqueGuestId(slug, listOf(slug, id)).length shouldBe 50
        }
    })
