package searchengine.util;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Connection;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import searchengine.config.Site;
import searchengine.model.*;
import searchengine.parser.HttpParserJsoup;
import searchengine.parser.LemmaParser;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.Map;

@Component
@Scope("prototype")
@RequiredArgsConstructor
@Slf4j
public class EntityCreator {
    private final HttpParserJsoup httpParserJsoup;
    private final LemmaParser lemmaParser;

    public PageEntity createPageEntity(String link, SiteEntity siteEntity) {
        int responseCode;
        PageEntity pageEntity = new PageEntity();
        Connection.Response response = null;
        pageEntity.setPath(link.substring(siteEntity.getUrl().length()));
        pageEntity.setSiteId(siteEntity);

        try {
            response = httpParserJsoup.getConnect(link).execute();
            responseCode = response.statusCode();
            pageEntity.setCode(responseCode);
            pageEntity.setContent((responseCode == 200) ? response.parse().toString() :
                    response.statusMessage());
        } catch (IOException e) {
            log.error("{}{} {} createPageEntity ", e, e.getMessage(), link);
            return pageEntity;
        }
        return pageEntity;
    }

    public SiteEntity createSiteEntity(Site site) {
        SiteEntity siteEntity = new SiteEntity();
        siteEntity.setName(site.getName());
        siteEntity.setUrl(site.getUrl());
        siteEntity.setStatusTime(LocalDateTime.now());

        try {
            httpParserJsoup.getConnect(site.getUrl()).execute().statusCode();
            siteEntity.setStatus(StatusType.INDEXING);
            siteEntity.setLastError("");
        } catch ( IOException e) {
            log.error(e.getMessage());
            siteEntity.setStatus(StatusType.FAILED);
            site.setIndexingIsStopped(true);
            log.info("{} {}", site.getUrl(), site.isIndexingIsStopped());
            siteEntity.setLastError(e.getClass() + e.getMessage());
        }
        return siteEntity;
    }

    public LemmaEntity createLemmaForPage(SiteEntity site, String lemma){
        LemmaEntity lemmaEntity = new LemmaEntity();
        lemmaEntity.setSiteId(site);
        lemmaEntity.setLemma(lemma);
        lemmaEntity.setFrequency(1);
        return lemmaEntity;
    }

    public IndexEntity createIndexEntity(PageEntity pageEntity, LemmaEntity lemmaEntity, Integer rank){
        IndexEntity indexEntity = new IndexEntity();
        indexEntity.setPageId(pageEntity);
        indexEntity.setLemmaId(lemmaEntity);
        indexEntity.setRank(rank.floatValue());

        return indexEntity;
    }
    public Map<String, Integer> getLemmaForPage(PageEntity pageEntity){
        return lemmaParser.getLemmasForPage(pageEntity);
    }
}
