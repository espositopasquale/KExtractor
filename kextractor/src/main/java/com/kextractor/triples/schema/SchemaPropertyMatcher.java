package com.kextractor.triples.schema;

public class SchemaPropertyMatcher {

    public static String matchPersonProperty(String relation) {
        if (relation == null)
            return null;
        String cleanRel = relation.toLowerCase().trim();

        // 1. GENDER & PRONOUNS
        if (equalsAny(cleanRel, "is male", "is female") || containsAny(cleanRel, "gender", "identify as"))
            return "gender";
        if (containsAny(cleanRel, "pronoun"))
            return "pronouns";

        // 2. BIOGRAPHICAL / LIFE EVENTS
        if (equalsAny(cleanRel, "born on") || containsAny(cleanRel, "birth date"))
            return "birthDate";
        if (containsAny(cleanRel, "born", "birth place"))
            return "birthPlace";

        if (equalsAny(cleanRel, "died", "died in") || containsAny(cleanRel, "death place", "pass away"))
            return "deathPlace";
        if (equalsAny(cleanRel, "died on") || containsAny(cleanRel, "death date"))
            return "deathDate";

        if (containsAny(cleanRel, "reside", "live", "dwell"))
            return "homeLocation";
        if (containsAny(cleanRel, "citizen", "nationality"))
            return "nationality";
        if (containsAny(cleanRel, "speak", "know language", "fluent"))
            return "knowsLanguage";
        if (containsAny(cleanRel, "height", "tall"))
            return "height";
        if (containsAny(cleanRel, "weight", "weigh"))
            return "weight";
        if (containsAny(cleanRel, "net worth", "wealth", "asset"))
            return "netWorth";

        // 3. EDUCATION & CAREER
        if (containsAny(cleanRel, "graduate", "alumni", "study", "attend"))
            return "alumniOf";
        if (containsAny(cleanRel, "work for", "employ", "staff at"))
            return "worksFor";
        if (containsAny(cleanRel, "job title", "work as", "professor", "occupation"))
            return "jobTitle";
        if (containsAny(cleanRel, "skill", "expert in", "competency"))
            return "skills";
        if (containsAny(cleanRel, "know about", "expertise"))
            return "knowsAbout";

        // 4. FAMILIAL & SOCIAL
        if (containsAny(cleanRel, "marry", "spouse", "husband", "wife"))
            return "spouse";
        if (containsAny(cleanRel, "parent", "father of", "mother of", "child", "son", "daughter"))
            return "children";
        if (containsAny(cleanRel, "sibling", "brother", "sister"))
            return "sibling";
        if (containsAny(cleanRel, "know", "colleague", "acquaintance"))
            return "knows";
        if (containsAny(cleanRel, "follow"))
            return "follows";
        if (containsAny(cleanRel, "related to"))
            return "relatedTo";

        // 5. ACHIEVEMENTS & BUSINESS
        if (containsAny(cleanRel, "win", "award", "receive"))
            return "award";
        if (containsAny(cleanRel, "found", "establish"))
            return "founder";
        if (containsAny(cleanRel, "own"))
            return "owns";
        if (containsAny(cleanRel, "tax id", "tin"))
            return "taxID";
        if (containsAny(cleanRel, "vat id", "vat"))
            return "vatID";
        if (containsAny(cleanRel, "duns"))
            return "duns";
        if (containsAny(cleanRel, "brand"))
            return "brand";

        // 6. CONTACT DETAILS
        if (containsAny(cleanRel, "address", "street"))
            return "address";
        if (containsAny(cleanRel, "email"))
            return "email";
        if (containsAny(cleanRel, "telephone", "phone", "call"))
            return "telephone";
        if (containsAny(cleanRel, "fax"))
            return "faxNumber";
        if (containsAny(cleanRel, "work location", "office at"))
            return "workLocation";

        // 7. PERFORMER & CREATIVE ROLES
        if (containsAny(cleanRel, "perform in", "act in", "star in"))
            return "performerIn";
        if (containsAny(cleanRel, "publish", "write", "author"))
            return "publishingPrinciples";

        return matchThingProperty(cleanRel);
    }

    public static String matchVerbalPart(String relation) {
        return matchPersonProperty(relation);
    }

    public static String matchOrganizationProperty(String relation) {
        if (relation == null)
            return null;
        String cleanRel = relation.toLowerCase().trim();

        // 1. CORPORATE & LEGAL IDENTITY
        if (containsAny(cleanRel, "legal name", "official name"))
            return "legalName";
        if (containsAny(cleanRel, "slogan", "motto"))
            return "slogan";
        if (containsAny(cleanRel, "tax id", "tin"))
            return "taxID";
        if (containsAny(cleanRel, "vat id", "vat"))
            return "vatID";
        if (containsAny(cleanRel, "duns"))
            return "duns";
        if (containsAny(cleanRel, "lei code"))
            return "leiCode";
        if (containsAny(cleanRel, "nonprofit", "charity status"))
            return "nonprofitStatus";

        // 2. HISTORY & FOUNDATION
        if (equalsAny(cleanRel, "found") || containsAny(cleanRel, "established", "created by"))
            return "founder";
        if (containsAny(cleanRel, "founded in", "established in"))
            return "foundingDate";
        if (containsAny(cleanRel, "founded at", "started in"))
            return "foundingLocation";
        if (containsAny(cleanRel, "dissolved", "closed down", "went bankrupt"))
            return "dissolutionDate";

        // 3. HIERARCHY, OWNERSHIP & STRUCTURE
        if (containsAny(cleanRel, "parent", "subsidiary of", "owned by"))
            return "parentOrganization";
        if (containsAny(cleanRel, "own", "acquire", "buy"))
            return "owns";
        if (containsAny(cleanRel, "subsidiary", "owns company"))
            return "subOrganization";
        if (containsAny(cleanRel, "department", "division"))
            return "department";
        if (containsAny(cleanRel, "brand"))
            return "brand";

        // 4. PERSONNEL & AFFILIATIONS
        if (containsAny(cleanRel, "employ", "work for", "hired"))
            return "employee";
        if (containsAny(cleanRel, "number of employee", "workforce size"))
            return "numberOfEmployees";
        if (containsAny(cleanRel, "alumni", "graduated from"))
            return "alumni";
        if (containsAny(cleanRel, "ceo", "legal representative", "headed by"))
            return "legalRepresentative";
        if (containsAny(cleanRel, "member", "belong to"))
            return "member";

        // 5. SPONSORSHIP & FUNDING
        if (containsAny(cleanRel, "sponsor", "fund"))
            return "sponsor";
        if (containsAny(cleanRel, "funder", "financed by"))
            return "funder";

        // 6. CONTACT & LOCATION
        if (containsAny(cleanRel, "address", "located at", "headquarters"))
            return "location";
        if (containsAny(cleanRel, "legal address"))
            return "legalAddress";
        if (containsAny(cleanRel, "email"))
            return "email";
        if (containsAny(cleanRel, "telephone", "phone", "call"))
            return "telephone";
        if (containsAny(cleanRel, "fax"))
            return "faxNumber";
        if (containsAny(cleanRel, "area served", "operate in"))
            return "areaServed";

        return matchThingProperty(cleanRel);
    }

    public static String matchEventProperty(String relation) {
        if (relation == null)
            return null;
        String cleanRel = relation.toLowerCase().trim();

        // 1. DATES, TIMES & DURATIONS
        if (containsAny(cleanRel, "start at", "begin on", "starts on", "kick off", "date"))
            return "startDate";
        if (containsAny(cleanRel, "end at", "finish on", "conclude on", "ends on"))
            return "endDate";
        if (containsAny(cleanRel, "door open", "admission start"))
            return "doorTime";
        if (containsAny(cleanRel, "last for", "duration", "run for"))
            return "duration";
        if (containsAny(cleanRel, "rescheduled from", "previously scheduled"))
            return "previousStartDate";

        // 2. LOCATION & STATUS
        if (containsAny(cleanRel, "take place in", "held at", "happen in", "located at", "venue", "hosted at"))
            return "location";
        if (containsAny(cleanRel, "status", "cancelled", "postponed", "rescheduled"))
            return "eventStatus";
        if (containsAny(cleanRel, "attendance mode", "online", "virtual", "in person"))
            return "eventAttendanceMode";

        // 3. PEOPLE & ORGANIZATIONS (ROLES)
        if (containsAny(cleanRel, "organize", "host", "put together by", "run by"))
            return "organizer";
        if (containsAny(cleanRel, "perform", "play at", "live performance by"))
            return "performer";
        if (containsAny(cleanRel, "starring", "actor", "feature actor"))
            return "actor";
        if (containsAny(cleanRel, "attend", "go to", "present at"))
            return "attendee";
        if (containsAny(cleanRel, "sponsor", "support", "backed by"))
            return "sponsor";
        if (containsAny(cleanRel, "fund", "financed by"))
            return "funder";
        if (containsAny(cleanRel, "direct", "director of"))
            return "director";
        if (containsAny(cleanRel, "compose", "music by"))
            return "composer";
        if (containsAny(cleanRel, "translate"))
            return "translator";

        // 4. HIERARCHY & STRUCTURE
        if (containsAny(cleanRel, "part of", "belong to festival", "event of"))
            return "superEvent";
        if (containsAny(cleanRel, "include", "feature sub-event", "consist of"))
            return "subEvent";

        // 5. WORKS & MEDIA
        if (containsAny(cleanRel, "feature work", "showcase", "exhibit"))
            return "workFeatured";
        if (containsAny(cleanRel, "perform work", "play song", "stage play"))
            return "workPerformed";
        if (containsAny(cleanRel, "record in", "capture on", "film at"))
            return "recordedIn";
        if (containsAny(cleanRel, "language", "spoken in"))
            return "inLanguage";

        // 6. TICKETING, CAPACITY & OFFERS
        if (containsAny(cleanRel, "offer", "ticket", "price", "cost", "sell"))
            return "offers";
        if (containsAny(cleanRel, "free", "no charge", "complimentary"))
            return "isAccessibleForFree";
        if (containsAny(cleanRel, "capacity", "maximum attendee", "hold up to", "room for"))
            return "maximumAttendeeCapacity";
        if (containsAny(cleanRel, "ticket left", "remaining spot"))
            return "remainingAttendeeCapacity";
        if (containsAny(cleanRel, "audience", "target group", "intended for"))
            return "audience";
        if (containsAny(cleanRel, "age range", "typical age", "rated for"))
            return "typicalAgeRange";

        return matchThingProperty(cleanRel);
    }

    public static String matchPlaceProperty(String relation) {
        if (relation == null)
            return null;
        String cleanRel = relation.toLowerCase().trim();

        // 1. CONTAINMENT & HIERARCHY
        if (containsAny(cleanRel, "located in", "situate in", "found in", "part of", "inside"))
            return "containedInPlace";
        if (containsAny(cleanRel, "contain", "hold", "encompass", "include area"))
            return "containsPlace";

        // 2. COORDINATES & GEOMETRY
        if (containsAny(cleanRel, "latitude"))
            return "latitude";
        if (containsAny(cleanRel, "longitude"))
            return "longitude";
        if (containsAny(cleanRel, "coordinates", "geo location"))
            return "geo";

        // 3. SPATIAL RELATIONSHIPS (DE-9IM standards mapped to natural language)
        if (containsAny(cleanRel, "cover by", "spanned by"))
            return "geoCoveredBy";
        if (containsAny(cleanRel, "cover", "span across"))
            return "geoCovers";
        if (containsAny(cleanRel, "cross", "intersect with", "cut through"))
            return "geoCrosses";
        if (containsAny(cleanRel, "overlap", "share border with", "touch"))
            return "geoTouches";

        // 4. ADDRESS & CONTACT INFO
        if (containsAny(cleanRel, "address", "street", "located at"))
            return "address";
        if (containsAny(cleanRel, "telephone", "phone", "call"))
            return "telephone";
        if (containsAny(cleanRel, "fax"))
            return "faxNumber";

        // 5. FACILITIES, AMENITIES & RULES
        if (containsAny(cleanRel, "amenity", "feature", "facility", "has a", "equipped with"))
            return "amenityFeature";
        if (containsAny(cleanRel, "drive through", "drive-thru"))
            return "hasDriveThroughService";
        if (containsAny(cleanRel, "smoke", "smoking allowed"))
            return "smokingAllowed";
        if (containsAny(cleanRel, "public access", "open to public"))
            return "publicAccess";
        if (containsAny(cleanRel, "free", "no entry fee"))
            return "isAccessibleForFree";

        // 6. BUSINESS & EVENT SPECIFICS
        if (containsAny(cleanRel, "host event", "happening here", "venue for"))
            return "event";
        if (containsAny(cleanRel, "opening hour", "open at", "close at"))
            return "openingHoursSpecification";
        if (containsAny(cleanRel, "book tour", "schedule visit"))
            return "tourBookingPage";
        if (containsAny(cleanRel, "capacity", "hold up to", "room for", "maximum attendee"))
            return "maximumAttendeeCapacity";
        if (containsAny(cleanRel, "map", "directions to"))
            return "hasMap";
        if (containsAny(cleanRel, "branch code", "store code"))
            return "branchCode";

        // 7. VISUALS
        if (containsAny(cleanRel, "photo", "photograph", "picture of place"))
            return "photo";

        return matchThingProperty(cleanRel);
    }

    public static String matchActionProperty(String relation) {
        if (relation == null)
            return null;
        String cleanRel = relation.toLowerCase().trim();

        // 1. ACTORS & PARTICIPATION
        if (containsAny(cleanRel, "performed by", "done by", "carried out by", "agent", "executed by"))
            return "agent";
        if (containsAny(cleanRel, "participate in", "assisted by", "with", "together with", "co-agent"))
            return "participant";
        if (containsAny(cleanRel, "provided by", "operated by", "service provider"))
            return "provider";

        // 2. OBJECTS, INSTRUMENTS & RESULTS
        if (containsAny(cleanRel, "act upon", "applied to", "target object", "affect"))
            return "object";
        if (containsAny(cleanRel, "using", "with instrument", "by means of", "via"))
            return "instrument";
        if (containsAny(cleanRel, "result in", "produced", "created", "led to", "caused"))
            return "result";
        if (containsAny(cleanRel, "target", "directed at", "aimed at"))
            return "target";

        // 3. TIME & LOCATION
        if (containsAny(cleanRel, "start time", "began at", "started at", "commenced at"))
            return "startTime";
        if (containsAny(cleanRel, "end time", "finished at", "ended at", "concluded at"))
            return "endTime";
        if (containsAny(cleanRel, "take place at", "occur at", "happen at", "location"))
            return "location";

        // 4. STATUS, PROCESS & ERRORS
        if (containsAny(cleanRel, "status", "disposition", "currently"))
            return "actionStatus";
        if (containsAny(cleanRel, "process", "how to", "method used", "procedure"))
            return "actionProcess";
        if (containsAny(cleanRel, "fail because", "error", "cause of failure", "failed due to"))
            return "error";

        return matchThingProperty(cleanRel);
    }

    private static String matchThingProperty(String cleanRel) {
        if (containsAny(cleanRel, "name", "called"))
            return "name";
        if (containsAny(cleanRel, "alias", "also known as", "dba"))
            return "alternateName";
        if (containsAny(cleanRel, "describe", "description"))
            return "description";

        if (containsAny(cleanRel, "url", "website"))
            return "url";
        if (containsAny(cleanRel, "image", "picture"))
            return "image";
        if (containsAny(cleanRel, "logo", "emblem"))
            return "logo";
        if (equalsAny(cleanRel, "id", "uuid"))
            return "identifier";

        return null;
    }

    private static boolean containsAny(String input, String... targets) {
        for (String target : targets) {
            if (input.contains(target)) {
                return true;
            }
        }
        return false;
    }

    private static boolean equalsAny(String input, String... targets) {
        for (String target : targets) {
            if (input.equals(target)) {
                return true;
            }
        }
        return false;
    }
}