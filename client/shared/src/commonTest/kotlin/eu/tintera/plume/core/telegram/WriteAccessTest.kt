package eu.tintera.plume.core.telegram

import kotlin.test.Test
import kotlin.test.assertEquals

class WriteAccessTest {

    @Test
    fun soukromyChatPiseVzdy() {
        assertEquals(WriteAccess.Allowed, writeAccess(isChannel = false, status = null, membersCanSend = false))
    }

    @Test
    fun vKanaluPiseJenVlastnikASpravceSPravem() {
        assertEquals(WriteAccess.Allowed, writeAccess(true, OwnMemberStatus.Creator(isMember = true), false))
        assertEquals(WriteAccess.Allowed, writeAccess(true, OwnMemberStatus.Admin(canPostMessages = true), false))
        assertEquals(WriteAccess.ChannelReadOnly, writeAccess(true, OwnMemberStatus.Admin(canPostMessages = false), false))
        assertEquals(WriteAccess.ChannelReadOnly, writeAccess(true, OwnMemberStatus.Member, true))
    }

    @Test
    fun veSkupineRozhodujiVychoziPrava() {
        assertEquals(WriteAccess.Allowed, writeAccess(false, OwnMemberStatus.Member, membersCanSend = true))
        assertEquals(WriteAccess.Restricted, writeAccess(false, OwnMemberStatus.Member, membersCanSend = false))
        // Správce smí psát i do skupiny, kde členové nesmějí.
        assertEquals(WriteAccess.Allowed, writeAccess(false, OwnMemberStatus.Admin(canPostMessages = false), false))
    }

    @Test
    fun omezenemuClenoviPlatiJehoVlastniPrava() {
        // Vlastní práva omezeného člena přebíjejí výchozí práva skupiny — oběma směry.
        assertEquals(WriteAccess.Restricted, writeAccess(false, OwnMemberStatus.Restricted(true, canSendMessages = false, content = ContentAccess.All), true))
        assertEquals(WriteAccess.Allowed, writeAccess(false, OwnMemberStatus.Restricted(true, canSendMessages = true, content = ContentAccess.All), false))
        assertEquals(WriteAccess.NotMember, writeAccess(false, OwnMemberStatus.Restricted(isMember = false, canSendMessages = true, content = ContentAccess.All), true))
    }

    @Test
    fun kdoVChatuNeniNepise() {
        assertEquals(WriteAccess.NotMember, writeAccess(false, OwnMemberStatus.Left, true))
        assertEquals(WriteAccess.NotMember, writeAccess(false, OwnMemberStatus.Banned, true))
        assertEquals(WriteAccess.NotMember, writeAccess(true, OwnMemberStatus.Creator(isMember = false), true))
    }

    // --- Druhy obsahu ---

    /** Skupina, kde smí jen text: fotky, videa, soubory, hlasovky ani samolepky ne. */
    private val jenText = ContentAccess(
        photos = false,
        videos = false,
        documents = false,
        audios = false,
        voiceNotes = false,
        videoNotes = false,
        stickers = false,
        polls = false,
        checklists = false,
    )

    @Test
    fun soukromyChatPosilaVsechno() {
        assertEquals(ContentAccess.All, contentAccess(status = null, groupDefaults = jenText))
    }

    @Test
    fun spravceAVlastnikNejsouOmezeni() {
        // Stejně jako u writeAccess: omezení skupiny jdou mimo ně.
        assertEquals(ContentAccess.All, contentAccess(OwnMemberStatus.Creator(isMember = true), jenText))
        assertEquals(ContentAccess.All, contentAccess(OwnMemberStatus.Admin(canPostMessages = false), jenText))
    }

    @Test
    fun clenoviPlatiVychoziPravaSkupiny() {
        assertEquals(jenText, contentAccess(OwnMemberStatus.Member, jenText))
        assertEquals(ContentAccess.All, contentAccess(OwnMemberStatus.Member, ContentAccess.All))
    }

    @Test
    fun omezenemuClenoviPlatiJehoVlastniDruhy() {
        // Vlastní práva přebíjejí výchozí — oběma směry, jako u psaní.
        val jenFotky = jenText.copy(photos = true)
        assertEquals(jenFotky, contentAccess(OwnMemberStatus.Restricted(true, true, jenFotky), ContentAccess.All))
        assertEquals(ContentAccess.All, contentAccess(OwnMemberStatus.Restricted(true, true, ContentAccess.All), jenText))
    }

    @Test
    fun neznamePostaveniNeomezuje() {
        // `chatAccess` vrací status `null`, když se ho nepodaří zjistit; chybějící sponka by
        // uživatele od chatu odřízla víc než chyba po odeslání.
        assertEquals(ContentAccess.All, contentAccess(null, jenText))
    }

    @Test
    fun soukromyChatSeNeupravuje() {
        // Nemá co upravovat — jméno je uživatelovo, ne naše.
        assertEquals(ChatEditRights(), chatEditRights(isChannel = false, status = null, membersCanChangeInfo = true))
    }

    @Test
    fun vlastnikSmiVsechno() {
        assertEquals(
            ChatEditRights(canChangeInfo = true, canRestrictMembers = true, canManageInvites = true),
            chatEditRights(false, OwnMemberStatus.Creator(isMember = true), membersCanChangeInfo = false),
        )
        // V kanálu není co omezovat: výchozí práva členů nemá.
        assertEquals(
            ChatEditRights(canChangeInfo = true, canRestrictMembers = false, canManageInvites = true),
            chatEditRights(true, OwnMemberStatus.Creator(isMember = true), membersCanChangeInfo = false),
        )
        // Vlastník, který ve skupině není, ji upravovat nemůže.
        assertEquals(ChatEditRights(), chatEditRights(false, OwnMemberStatus.Creator(isMember = false), true))
    }

    @Test
    fun spravceSmiJenToCoMaVPravech() {
        val jenInfo = OwnMemberStatus.Admin(canPostMessages = true, canChangeInfo = true, canRestrictMembers = false)
        assertEquals(ChatEditRights(canChangeInfo = true), chatEditRights(false, jenInfo, false))

        val jenPrava = OwnMemberStatus.Admin(canPostMessages = true, canChangeInfo = false, canRestrictMembers = true)
        assertEquals(ChatEditRights(canRestrictMembers = true), chatEditRights(false, jenPrava, false))

        // Správce, který smí jen zvát, dostane jen pozvánky — v kanálu stejně jako ve skupině.
        val jenZvat = OwnMemberStatus.Admin(canPostMessages = true, canInviteUsers = true)
        assertEquals(ChatEditRights(canManageInvites = true), chatEditRights(false, jenZvat, false))
        assertEquals(ChatEditRights(canManageInvites = true), chatEditRights(true, jenZvat, false))

        // Správce bez jediného z nich nabídku úpravy vůbec nedostane.
        assertEquals(ChatEditRights(), chatEditRights(false, OwnMemberStatus.Admin(canPostMessages = true), false))
    }

    @Test
    fun clenMeniInformaceJenKdyzToSkupinaDovoli() {
        assertEquals(ChatEditRights(canChangeInfo = true), chatEditRights(false, OwnMemberStatus.Member, membersCanChangeInfo = true))
        assertEquals(ChatEditRights(), chatEditRights(false, OwnMemberStatus.Member, membersCanChangeInfo = false))
        // Práva členů nesmí měnit nikdy — `setChatPermissions` chce právo omezovat.
        assertEquals(false, chatEditRights(false, OwnMemberStatus.Member, true).canRestrictMembers)
        // V kanálu člen neupravuje nic, ani když výchozí práva tvrdí opak.
        assertEquals(ChatEditRights(), chatEditRights(true, OwnMemberStatus.Member, membersCanChangeInfo = true))
    }

    @Test
    fun omezenyClenSeRidiSvymiPravy() {
        val smi = OwnMemberStatus.Restricted(true, canSendMessages = false, content = ContentAccess.All, canChangeInfo = true)
        // Vlastní práva přebíjejí výchozí — stejně jako u psaní.
        assertEquals(ChatEditRights(canChangeInfo = true), chatEditRights(false, smi, membersCanChangeInfo = false))

        val nesmi = OwnMemberStatus.Restricted(true, canSendMessages = true, content = ContentAccess.All, canChangeInfo = false)
        assertEquals(ChatEditRights(), chatEditRights(false, nesmi, membersCanChangeInfo = true))

        // Kdo ve skupině není, neupravuje.
        val pryc = OwnMemberStatus.Restricted(false, canSendMessages = true, content = ContentAccess.All, canChangeInfo = true)
        assertEquals(ChatEditRights(), chatEditRights(false, pryc, true))
    }

    @Test
    fun kdoVChatuNeniNeupravuje() {
        assertEquals(ChatEditRights(), chatEditRights(false, OwnMemberStatus.Left, membersCanChangeInfo = true))
        assertEquals(ChatEditRights(), chatEditRights(false, OwnMemberStatus.Banned, membersCanChangeInfo = true))
    }

    @Test
    fun sponkaSeNekresliBezJedinehoDruhuPrilohy() {
        assertEquals(false, jenText.anyAttachment)
        // Hlasovky a samolepky sponku neoživí — ty se posílají jinudy.
        assertEquals(false, jenText.copy(voiceNotes = true, stickers = true).anyAttachment)
        assertEquals(true, jenText.copy(documents = true).anyAttachment)
        assertEquals(true, jenText.copy(photos = true).anyAttachment)
        // Anketa se zakládá ze sponky taky — sama ji oživí.
        assertEquals(true, jenText.copy(polls = true).anyAttachment)
        // Seznam úkolů taky.
        assertEquals(true, jenText.copy(checklists = true).anyAttachment)
    }
}

class TopicWriteAccessTest {

    @Test
    fun otevreneTemaNemeniNic() {
        assertEquals(WriteAccess.Allowed, topicWriteAccess(WriteAccess.Allowed, OwnMemberStatus.Member, topicClosed = false))
        assertEquals(WriteAccess.Restricted, topicWriteAccess(WriteAccess.Restricted, OwnMemberStatus.Member, topicClosed = false))
    }

    @Test
    fun doUzavrenehoPiseJenVlastnikASpravceTemat() {
        assertEquals(WriteAccess.TopicClosed, topicWriteAccess(WriteAccess.Allowed, OwnMemberStatus.Member, topicClosed = true))
        assertEquals(
            WriteAccess.TopicClosed,
            topicWriteAccess(WriteAccess.Allowed, OwnMemberStatus.Admin(canPostMessages = false), topicClosed = true),
        )
        assertEquals(
            WriteAccess.Allowed,
            topicWriteAccess(WriteAccess.Allowed, OwnMemberStatus.Admin(canPostMessages = false, canManageTopics = true), topicClosed = true),
        )
        assertEquals(WriteAccess.Allowed, topicWriteAccess(WriteAccess.Allowed, OwnMemberStatus.Creator(isMember = true), topicClosed = true))
    }

    @Test
    fun duvodZeSkupinyMaPrednost() {
        // Kdo ve skupině není, se to má dozvědět — ne že je téma uzavřené.
        assertEquals(WriteAccess.NotMember, topicWriteAccess(WriteAccess.NotMember, OwnMemberStatus.Left, topicClosed = true))
    }

    @Test
    fun diskusniSkupinaDovoliPsatBezVstupu() {
        assertEquals(WriteAccess.Allowed, writeAccess(false, OwnMemberStatus.Left, membersCanSend = true, nonMembersCanSend = true))
        // Výchozí práva skupiny platí i pro nečlena.
        assertEquals(WriteAccess.Restricted, writeAccess(false, OwnMemberStatus.Left, membersCanSend = false, nonMembersCanSend = true))
        assertEquals(WriteAccess.NotMember, writeAccess(false, OwnMemberStatus.Left, membersCanSend = true, nonMembersCanSend = false))
        // Vyhozenému to nepomůže, kanálu taky ne.
        assertEquals(WriteAccess.NotMember, writeAccess(false, OwnMemberStatus.Banned, membersCanSend = true, nonMembersCanSend = true))
        assertEquals(WriteAccess.NotMember, writeAccess(true, OwnMemberStatus.Left, membersCanSend = true, nonMembersCanSend = true))
    }
}
