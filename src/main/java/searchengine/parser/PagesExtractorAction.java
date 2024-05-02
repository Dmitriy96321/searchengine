package searchengine.parser;


import lombok.extern.slf4j.Slf4j;
import searchengine.config.LettuceCach;
import searchengine.config.Site;
import searchengine.model.*;
import searchengine.repositories.IndexesRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PagesRepository;
import searchengine.repositories.SitesRepository;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;

@Slf4j
public class PagesExtractorAction extends RecursiveAction {

    private Site site;
    private SiteEntity siteEntity;
    private String url;
    private final HttpParserJsoup httpParserJsoup;
    private final PagesRepository pagesRepository;
    private final SitesRepository sitesRepository;
    private final LemmaRepository lemmasRepository;
    private final IndexesRepository indexRepository;
    private final ForkJoinPool thisPool;
    private final EntityCreator entityCreator;
    private final LettuceCach lettuceCach;
    private  Map<String,LemmaEntity> lemmasCache;


    public PagesExtractorAction(Site site, HttpParserJsoup httpParserJsoup,
                                PagesRepository pagesRepository, SitesRepository sitesRepository,
                                LemmaRepository lemmasRepository, IndexesRepository indexRepository,
                                ForkJoinPool thisPool, EntityCreator entityCreator) {
        this.entityCreator = entityCreator;
        this.site = site;
        this.siteEntity = entityCreator.createSiteEntity(site);
        this.url = site.getUrl();
        this.httpParserJsoup = httpParserJsoup;
        this.pagesRepository = pagesRepository;
        this.sitesRepository = sitesRepository;
        this.thisPool = thisPool;
        this.lemmasRepository = lemmasRepository;
        this.indexRepository = indexRepository;
        sitesRepository.save(siteEntity);
        this.lettuceCach = new LettuceCach(siteEntity);
        this.lemmasCache =  new ConcurrentHashMap<>();
    }


    public PagesExtractorAction(SiteEntity siteEntity, String url, Site site,
                                HttpParserJsoup httpParserJsoup, PagesRepository pagesRepository,
                                LemmaRepository lemmasRepository, IndexesRepository indexRepository,
                                SitesRepository sitesRepository, ForkJoinPool thisPool,
                                EntityCreator entityCreator, LettuceCach lettuceCach,
                                Map<String,LemmaEntity> lemmasCache) {


        this.entityCreator = entityCreator;
        this.site = site;
        this.siteEntity = siteEntity;
        this.url = url;
        this.httpParserJsoup = httpParserJsoup;
        this.pagesRepository = pagesRepository;
        this.sitesRepository = sitesRepository;
        this.thisPool = thisPool;
        this.lemmasRepository = lemmasRepository;
        this.indexRepository = indexRepository;
        this.lettuceCach = lettuceCach;
        this.lemmasCache = lemmasCache;
    }

    @Override
    protected void compute() {

        Set<PagesExtractorAction> taskList = new HashSet<>();
        Set<String> links = httpParserJsoup.extractLinks(url);

        for (String link : links) {
            if (site.isIndexingIsStopped()) {
                return;
            }
                if (lettuceCach.addSet(link)) {
                    PageEntity pageEntity = entityCreator.createPageEntity(link,siteEntity);
                    pagesRepository.save(pageEntity);

                    saveLemmas(pageEntity);
//                    log.info("Add lemmas for {}", link);
                    saveTime();
                    PagesExtractorAction task = new PagesExtractorAction(siteEntity, link, site,
                            httpParserJsoup, pagesRepository,
                            lemmasRepository, indexRepository,
                            sitesRepository, thisPool, entityCreator, lettuceCach, lemmasCache);
                    task.fork();
                    taskList.add(task);

                }

        }
        for (PagesExtractorAction task : taskList) {
            task.join();
        }

        if (!site.isIndexingIsStopped()) {
            siteEntity.setStatus(StatusType.INDEXED);
            sitesRepository.setStatusBySite(StatusType.INDEXED, siteEntity.getId());
//            concurrentSet.close();
        }
    }
    private void saveTime(){
        LocalDateTime time = LocalDateTime.now();
        siteEntity.setStatusTime(time);
        sitesRepository.setStatusTime(time, siteEntity.getId());
    }
    private void saveLemmas(PageEntity pageEntity){
        List<IndexEntity> indexEntityList = new ArrayList<>();
        List<LemmaEntity> newLemmasEntityList = new ArrayList<>();
        List<LemmaEntity> updateLemmasEntityList = new ArrayList<>();
        entityCreator.getLemmaForPage(pageEntity).forEach((lemma, frequency) -> {
            if (lettuceCach.addSet(lemma)) {
                LemmaEntity lemmaEntity = entityCreator.createLemmaForPage(siteEntity, lemma, frequency);
                lemmasCache.put(lemma, lemmaEntity);
                newLemmasEntityList.add(lemmaEntity);
                indexEntityList.add(entityCreator.createIndexEntity(pageEntity, lemmaEntity, frequency));
            }else {
                LemmaEntity lemmaEntity = lemmasCache.get(lemma);
                indexEntityList.add(entityCreator.createIndexEntity(pageEntity, lemmaEntity, frequency));
                Integer newFrequency = lemmaEntity.getFrequency() + 1;
//                log.info(lemmaEntity.toString());
//                lemmaEntity.setSiteId(siteEntity);
                lemmaEntity.setFrequency(newFrequency);
//                lemmasRepository.saveAndFlush(lemmaEntity);
//                updateLemmasEntityList.add(lemmaEntity);
                lemmasRepository.save(lemmaEntity);
            }
        });
        lemmasRepository.saveAll(newLemmasEntityList);
        indexRepository.saveAll(indexEntityList);
//        lemmasRepository.saveAllAndFlush(updateLemmasEntityList);
//        saveIndex(pageEntity,newLemmasEntityList);

    }




}
