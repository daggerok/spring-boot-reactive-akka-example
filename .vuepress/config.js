const baseHref = process.env.BASE_HREF;
const base = !baseHref ? '/' : baseHref;

module.exports = {
  head: [
    ['link', { rel: 'icon', href: '/favicon.ico' }]
  ],
  base,
  themeConfig: {
    repo: 'daggerok/spring-boot-reactive-akka-example',
    lastUpdated: 'Updated at',
  }
};
