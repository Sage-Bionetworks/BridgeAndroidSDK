# hack to get latest ResearchStack code since artficts aren't being published
if [[ $TRAVIS_BRANCH != 'master' ]]; then
  git clone -b develop https://github.com/ResearchStack/ResearchStack.git
  pushd ResearchStack
  ./gradlew install
  popd
fi
