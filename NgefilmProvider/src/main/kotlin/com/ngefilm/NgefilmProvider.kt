package com.ngefilm

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class NgefilmProvider : MainAPI() {
    override var mainUrl = "https://new39.ngefilm.site"
    override var name = "Ngefilm"
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override var lang = "id"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/page/" to "Latest Movies",
        "$mainUrl/genre/action/page/" to "Action",
        "$mainUrl/genre/drama/page/" to "Drama",
        "$mainUrl/genre/horror/page/" to "Horror"
    )

    // 1. Mengambil konten di Beranda (Homepage)
    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if (page == 1) request.data.replace("/page/", "") else "${request.data}$page"
        val document = app.get(url).document
        val homeItems = ArrayList<HomePageList>()

        val items = document.select("article.item, div.item-infinite, .items article").mapNotNull {
            it.toSearchResult()
        }
        homeItems.add(HomePageList(request.name, items))
        return HomePageResponse(homeItems)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.select("h2.entry-title, h3.title, .entry-title a, .data h3 a").text().trim()
        if (title.isEmpty()) return null

        val href = fixUrl(this.select("a").firstOrNull()?.attr("href") ?: return null)
        val posterUrl = fixUrlNull(this.select("img").firstOrNull()?.let { 
            it.attr("data-src").ifEmpty { it.attr("src") } 
        })
        val quality = this.select(".quality, .gmr-quality-item").text().trim()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            addQuality(quality)
        }
    }

    // 2. Pencarian Film
    override suspend fun search(query: String): List<SearchResponse> {
        val searchUrl = "$mainUrl/?s=$query&post_type[]=post&post_type[]=tv"
        val document = app.get(searchUrl).document
        return document.select("article.item, div.item-infinite, .search-item, .result-item article").mapNotNull {
            it.toSearchResult()
        }
    }

    // 3. Detail Film / Sinopsis
    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title = document.select("h1.entry-title, h1.title").text().trim()
        val poster = fixUrlNull(document.select(".poster img, .entry-content img, .gmr-movie-data img").attr("src"))
        val plot = document.select(".entry-content p, .synopsis p, .description p").text().trim()
        val year = document.select(".year, .gmr-movie-data span:contains(Year) a, .date").text().filter { it.isDigit() }.toIntOrNull()
        val tags = document.select(".genre a, .gmr-movie-data span:contains(Genre) a").map { it.text() }

        // Memeriksa apakah tipe konten merupakan serial / TV Series
        val isTvSeries = document.select("div.episodios, ul.episodios, .gmr-listseries").isNotEmpty()

        return if (isTvSeries) {
            val episodes = ArrayList<Episode>()
            document.select(".episodios li, .gmr-listseries a").forEachIndexed { index, elem ->
                val epHref = fixUrl(elem.select("a").attr("href").ifEmpty { elem.attr("href") })
                val epName = elem.select(".episodiotitle a, a").text().ifEmpty { "Episode ${index + 1}" }
                episodes.add(Episode(epHref, epName, episode = index + 1))
            }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = plot
                this.year = year
                this.tags = tags
            }
        }
    }

    // 4. Mengekstrak Link Video Player
    override suspend fun loadLinks(
        data: String,
        isCdn: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document

        // Mengambil semua iframe pemutar video yang ada di halaman
        val iframes = document.select("iframe, .gmr-embed-responsive iframe, #embed-player iframe")
            .mapNotNull { fixUrlNull(it.attr("src").ifEmpty { it.attr("data-src") }) }

        for (iframe in iframes) {
            // Meload extractor bawaan Cloudstream (Streamwish, Filelions, dood, dll.)
            loadExtractor(iframe, data, subtitleCallback, callback)
        }
        return true
    }
}